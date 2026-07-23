package com.tgac.logic.tabling;

// ABOUTME: Tabled evaluation of logic goals: answers are cached per call and shared,
// ABOUTME: which makes left-recursive and mutually recursive predicates terminate.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.functional.fibers.primitives.Region;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Residue;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.goals.optimizer.Barrier;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * Provides tabling (memoization) for logic goals to prevent infinite loops
 * and improve performance by caching answers.
 *
 * The first application of a call becomes the master and executes the body
 * with a caching hook threaded through its continuation: every derived
 * answer is reified, deduplicated and cached before it flows on. Later
 * applications consume the cache. Nothing ever blocks — a consumer that
 * exhausts the cache parks its continuation in the table entry as data and
 * terminates, and whoever derives a new answer respawns the parked consumers
 * as detached fibers. The search reaches its fixpoint when the scheduler
 * runs out of work; parked consumers left at that point are failed branches.
 *
 * Committed choice (conda/condu/orElse) over tabled goals is undefined
 * behavior: commitment depends on table state, incomplete tables never
 * signal failure, and pruning a branch cannot undo its table effects.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Tabling {

	/**
	 * Define a tabled relation over its formal parameters.
	 *
	 * <pre>
	 * Tabled&lt;Tuple2&lt;Unifiable&lt;Integer&gt;, Unifiable&lt;Integer&gt;&gt;&gt; path =
	 *     Tabling.defineRecursive(self -&gt; args -&gt; args.apply((x, y) -&gt;
	 *         edge(x, y).or(defer(() -&gt; exist(z -&gt;
	 *             self.apply(Tuple.of(x, z)).and(edge(z, y)))))));
	 * </pre>
	 *
	 * The cache is keyed on the relation's identity, so a relation must be
	 * created exactly once — a body that calls define mints a new cache per
	 * call. Name goals via {@link Goal#named} where traces should be legible.
	 *
	 * @param body the relation body, given the recursion handle and the
	 * 		argument tuple; use {@link #define(Function)} when the handle
	 * 		is not needed
	 */
	public static <T> Tabled<T> defineRecursive(Function<Tabled<T>, Function<T, Goal>> body) {
		return new Tabled<>(body);
	}

	/**
	 * Define a tabled relation without the recursion handle: non-recursive,
	 * or recursing through a field read at goal execution time.
	 */
	public static <T> Tabled<T> define(Function<T, Goal> body) {
		return defineRecursive(self -> body);
	}

	/**
	 * The tabled goal behind {@link Tabled#apply}:
	 *
	 * <pre>
	 * 1. Reify the argument tuple in the current state to create the lookup key
	 * 2. Look up the call in the solve-scoped table
	 * 3. A new call detaches an ANONYMOUS MASTER that executes the body, caching answer terms
	 * 4. Every caller consumes cached answer terms, parking when it catches up
	 * </pre>
	 */
	/*
	 * THE ONE RULE: state follows the data, the coat follows the CODE. A
	 * package's EnclosingCall coat names the innermost tabled call whose
	 * execution this code is part of, and it changes exactly where control
	 * crosses a call boundary — nowhere else:
	 *
	 *   callerPkg   the state at this call site, wearing the coat of
	 *               whatever call the site is executing inside (top: none).
	 *   bodyPkg     callerPkg re-coated with THIS entry on call ENTRY — the
	 *               coat is mail for the calls written inside the body.
	 *   answerPkg   a body success, wearing this call's coat; it ENDS at the
	 *               answer cell. Answers reach callers only through their
	 *               consumers, each running under its own caller's coat.
	 *
	 * Forks inherit the coat, parked registrations freeze it, wakes resume
	 * it. A registration's coat is how billing works: the entry it parks IN
	 * is what it waits for; the coat says which call's execution it belongs
	 * to, and so whose ledger pays for its work.
	 */
	static <T> Goal tabled(Tabled<T> relation, T args, Supplier<Goal> body) {
		// a bare Unifiable is an equality ATOM to decompose (no wrapped-Term
		// kind: tuple MEMBERS decompose via wrapTerm, a bare wrapping does
		// not), which would collapse every answer into one. Wrap it in a
		// Tuple1 internally — keys, answers and consumption all take the
		// structural path; the body still receives the bare argument
		Unifiable<?> argsTerm = lval(args instanceof Term ? Tuple.of(args) : args);
		// keyed widening: the call pattern is the table key, so no optimizer may
		// move binders across it — the contract as a type, not an accident of opacity
		return Barrier.priced(p -> tabledOrder(p, relation, argsTerm), callerPkg -> k ->
				MiniKanren.reifyWithHoles(callerPkg.substitution(), argsTerm.getObjectTerm()).flatMap(reified -> {
					// the call's REGION: reify anonymizes the vars, project anonymizes
					// the knowledge about them — positionally, residue slot i = the
					// hole reify names _.i, by construction. Non-projectable knowledge
					// cannot enter the key, and unkeyed knowledge means wrong reuse.
					Reified<?> reifiedArgs = reified._1;
					Projection projection = Projection.of(callerPkg, reified._2);
					Call key = Call.of(relation, reifiedArgs, projection.getResidues());
					Table table = callerPkg.getStore(Table.class);
					// a weighted solve whose semiring cannot table (non-idempotent,
					// non-closed) would silently drop weights here — refuse loudly
					table.assertTablingAllowed();
					// subsumptive reuse: a sealed general entry is a read-only relation
					// containing every answer this instance call could produce (subset
					// property) — read it through consume's unification filter
					TableEntry<Object> subsumer = table.reusableSubsumer(key);
					if (subsumer != null) {
						return consume(subsumer, k, callerPkg, argsTerm, 0, table);
					}
					TableEntry<Object> entry = table.getOrCreateEntry(key);
					if (entry.tryBecomeMaster()) {
						// the ANONYMOUS MASTER: the body runs as detached work billed
						// to this entry — it belongs to no caller. Every caller, the
						// first included, reads the cell as a consumer; the sleeper it
						// parks is the dependency edge completion detection needs, so
						// a caller can never seal ahead of a call it depends on. The
						// body runs FROM THE KEY: the first caller's constraint stores
						// are stripped and the key's residues restated, so the cache
						// holds exactly the region the key names — every caller
						// (the first included) filters at consumption by its own state
						Package bodyPkg = stripConstraints(table.bodyState(callerPkg))
								.putStore(new EnclosingCall(entry));
						Goal seeded = projection.seed(body.get());
						// the seal fires EMIT: the drained subscribers are its targets
						entry.getRegion().onSealed(drained -> table.sealed(entry, drained));
						return Fiber.detach(Region.track(entry.getRegion(),
										produce(entry, seeded, bodyPkg, argsTerm, table)))
								.flatMap(__ -> consume(entry, k, callerPkg, argsTerm, 0, table));
					}
					return consume(entry, k, callerPkg, argsTerm, 0, table);
				}));
	}

	/**
	 * The ∞→exact transition (docs/design/optimizer.md): an incomplete entry
	 * prices MAX — a barrier — because its answer count is still growing; a
	 * completed entry prices its exact count. Sound under reordering:
	 * execution is always at-or-more-bound than pricing, and a more-bound
	 * variant emits a subset of the priced variant's answers.
	 */
	private static <T> long tabledOrder(Package p, Tabled<T> relation, Unifiable<?> argsTerm) {
		return p.getStores().get(Table.class)
				.map(Table.class::cast)
				.map(table -> {
					Call key = Call.of(relation,
							MiniKanren.reify(p.substitution(), argsTerm).get());
					TableEntry<?> entry = table.getEntry(key);
					// a sealed subsumer's count bounds the instance's emissions
					// (subset property) — the same lookup reuse consumes through
					return entry != null ? entry : table.findSealedSubsumer(key);
				})
				.filter(entry -> entry != null && entry.isComplete())
				.map(entry -> (long) entry.getAnswerCount())
				.getOrElse(Long.MAX_VALUE);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Region<JoinMap<Reified<?>, Object>, Registration> regionOf(TableEntry entry) {
		return entry == null ? null : entry.getRegion();
	}

	/**
	 * The caller's constraint knowledge about the call vars, projected per
	 * store: the residues that join the {@link Call} key, and the restate
	 * goals that seed the master's body with exactly that knowledge. A store
	 * that cannot project cannot enter the key, and unkeyed knowledge means
	 * silently wrong reuse — refused loudly. A ⊤ residue (nothing known about
	 * the call vars) stays out of the key, so calls under irrelevant
	 * knowledge stay constraint-free variants.
	 */
	@Value
	private static class Projection {
		Map<Class<?>, Object> residues;
		java.util.List<Goal> restates;

		@SuppressWarnings({"unchecked", "rawtypes"})
		static Projection of(Package callerPkg, java.util.List<LVar<?>> callVars) {
			Map<Class<?>, Object> residues = HashMap.empty();
			java.util.List<Goal> restates = new ArrayList<>();
			java.util.List<Unifiable<?>> targets = new ArrayList<>(callVars);
			for (Packaged store : callerPkg.getStores().values()) {
				if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
					continue;
				}
				if (!(store instanceof Projectable)) {
					throw new IllegalStateException(
							"Tabling cannot key constraints it cannot project: non-empty "
									+ store.getClass().getSimpleName() + " at a tabled call");
				}
				Projectable projectable = (Projectable) store;
				// call side: widening allowed — escapes stay caller-private,
				// sound by containment, filtered at consumption
				Residue residue = projectable.project(callVars, true);
				Residue top = projectable.project(Collections.emptyList(), true);
				if (!residue.equals(top)) {
					residues = residues.put(store.getClass(), residue);
					restates.add(residue.restate(targets));
				}
			}
			return new Projection(residues, restates);
		}

		/** The master's goal: the key's knowledge re-imposed, then the body. */
		Goal seed(Goal body) {
			if (restates.isEmpty()) {
				return body;
			}
			Goal seeded = body;
			for (int i = restates.size() - 1; i >= 0; i--) {
				seeded = Conjunction.of(restates.get(i), seeded);
			}
			return seeded;
		}
	}

	/** Every residue re-imposed onto the instantiation's fresh holes — ground
	 * answers restate nothing and the goal is success. */
	@SuppressWarnings("unchecked")
	private static Goal restateAll(Map<Class<?>, Object> residues, java.util.List<LVar<?>> freshHoles) {
		java.util.List<Unifiable<?>> targets = new ArrayList<Unifiable<?>>(freshHoles);
		Goal seeded = Goal.success();
		for (Tuple2<Class<?>, Object> entry : residues) {
			seeded = Conjunction.of(seeded, ((Residue<?>) entry._2).restate(targets));
		}
		return seeded;
	}

	/** Remove every constraint-store factor: absence is ⊤, posting re-registers. */
	private static Package stripConstraints(Package pkg) {
		Package result = pkg;
		for (Packaged store : pkg.getStores().values()) {
			if (store instanceof ConstraintStore) {
				result = result.withoutStore(store.getClass());
			}
		}
		return result;
	}

	/**
	 * An answer's residues: each projecting store's LIVE knowledge over the
	 * answer's holes, demanded EXACT — an escape to a body-local throws (the
	 * store's refusal; ground the local or lift it into the answer). Spent
	 * bookkeeping is waved through by {@link Projectable#discharged} — the
	 * ground-answer fast path. Non-projectable live knowledge still refuses,
	 * and constrained answers under a mode that cannot replay them (closed)
	 * refuse before caching.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Map<Class<?>, Object> answerResidues(
			Package answerPkg, java.util.List<LVar<?>> answerHoles, Table table) {
		Map<Class<?>, Object> residues = HashMap.empty();
		for (Packaged store : answerPkg.getStores().values()) {
			if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
				continue;
			}
			if (store instanceof Projectable && ((Projectable<?>) store).discharged(answerPkg)) {
				continue;
			}
			if (!(store instanceof Projectable)) {
				throw new IllegalStateException(
						"Tabling does not capture constraints yet: non-empty "
								+ store.getClass().getSimpleName() + " on a tabled answer");
			}
			Projectable projectable = (Projectable) store;
			Residue residue = projectable.project(answerHoles, false);
			// the triviality baseline compares, it does not transcribe — never
			// demand exactness of a probe over no vars
			Residue top = projectable.project(Collections.emptyList(), true);
			if (!residue.equals(top)) {
				residues = residues.put(store.getClass(), residue);
			}
		}
		if (!residues.isEmpty() && !table.supportsConstrainedAnswers()) {
			throw new IllegalStateException(
					"constrained answers under closed/star tabling are not designed: "
							+ "weights over conditional answers is an orthogonal, open concern");
		}
		return residues;
	}

	/**
	 * The anonymous master: execute the body as a pure producer. Each new
	 * answer is cached and the consumers parked on the entry are respawned —
	 * the answer reaches callers only through their consumers, so this
	 * fiber's completion means BODY EXHAUSTED, the event the counters need.
	 * Duplicate answers fail their branch.
	 */
	private static Fiber<Nothing> produce(
			TableEntry<Object> entry,
			Goal goal,
			Package bodyPkg,
			Unifiable<?> argsTerm,
			Table table) {
		return goal.apply(bodyPkg).apply(answerPkg -> {
			// the coat is the canary: a goal that returned a fresh package instead
			// of deriving from its input shed every store — the damage downstream
			// is SILENT (answers reified over fresh substitutions cache
			// over-general; readers spawned uncoated go unbilled, so this entry
			// could seal under them and lose answers) — so refuse loudly here
			if (EnclosingCall.entryOf(answerPkg) != entry) {
				throw new IllegalStateException(
						"a goal inside a tabled body dropped its stores: packages must be "
								+ "derived from the incoming one, never minted fresh "
								+ "(the body's EnclosingCall coat is missing or foreign)");
			}
			return MiniKanren.reifyWithHoles(answerPkg.substitution(), argsTerm.getObjectTerm())
					.flatMap(reified -> {
						Map<Class<?>, Object> residues = answerResidues(answerPkg, reified._2, table);
						// what the cell caches: the term and the value this derivation
						// carries — caller-agnostic, since the body ran from ONE
						Tuple2<Reified<?>, Object> cached = table.capture(entry, answerPkg, reified._1);
						return entry.addAnswer(AnswerKey.of(cached._1, residues), cached._2)
								.map(parked -> respawn(entry, parked, table))
								.getOrElse(() -> done(nothing()));
					});
		});
	}

	/**
	 * Respawn parked consumers as detached fibers so they pick up the answers
	 * cached since they parked. Whoever derives an answer drives its consequences.
	 */
	private static Fiber<Nothing> respawn(TableEntry<Object> entry, List<Registration> parked, Table table) {
		Fiber<Nothing> result = done(nothing());
		for (Registration r : parked) {
			TableEntry<?> enclosingCall = r.getEnclosingCall();
			Fiber<Nothing> consumer = Fiber.defer(() ->
					consume(entry, r.getContinuation(), r.getPkg(), r.getArgsTerm(), r.getNextIndex(), table));
			// count the respawned consumer as RUNNING before removing its SLEEPING
			// record — the transition must never leave a window where the sleeper is
			// gone but the running count has not risen, or a racing seal (parallel
			// schedulers) reads the owner as quiescent and seals it out from under
			// this consumer, losing the answers it would derive. Over-counting for
			// the instant between only delays a seal, which is always sound.
			Fiber<Nothing> tracked = Region.track(regionOf(enclosingCall), consumer);
			if (enclosingCall != null) {
				enclosingCall.getRegion().awake(r);
			}
			result = result.flatMap(__ -> Fiber.detach(tracked));
		}
		return result;
	}

	/**
	 * Consumer: unify instantiated cached answers with the argument tuple,
	 * yielding each success to the continuation. On catching up with the
	 * cache the consumer parks itself in the entry and terminates —
	 * {@link #respawn} continues it when new answers arrive, and the
	 * fixpoint abandons it otherwise.
	 */
	private static Fiber<Nothing> consume(
			TableEntry<Object> entry,
			Fiber.Fn<Package, Nothing> k,
			Package callerPkg,
			Unifiable<?> argsTerm,
			int index,
			Table table) {

		if (index < entry.getAnswerCount()) {
			Tuple2<AnswerKey, Object> answer = entry.getAnswerAt(index);
			AnswerKey key = answer._1;
			Object cellValue = answer._2;
			// Fresh variables per consumption, so separate consumptions of the
			// same answer don't alias each other's free variables; a conditional
			// answer then RESTATES its residues onto the fresh holes before
			// delivery — the meet-at-consumption (a violated residue silently
			// fails the delivery, exactly like a failed unification)
			return MiniKanren.instantiateWithHoles(key.getTerm()).flatMap(inst ->
					MiniKanren.unify(callerPkg.substitution(), argsTerm.getObjectTerm(), inst._1.getObjectTerm())
							.map(callerPkg::withSubstitutions)
							.map(unifiedPkg -> restateAll(key.getResidues(), inst._2)
									.apply(unifiedPkg)
									// streaming ⊗s the cell value in; closed records the loop
									.apply(constrainedPkg -> k.apply(table.absorb(constrainedPkg,
											entry, key.getTerm(), cellValue, EnclosingCall.entryOf(callerPkg))))
									.flatMap(__ -> Fiber.defer(() ->
											consume(entry, k, callerPkg, argsTerm, index + 1, table))))
							.getOrElse(() -> Fiber.defer(() ->
									consume(entry, k, callerPkg, argsTerm, index + 1, table)))
							.flatMap(fib -> fib));
		}

		return parkWhenCaughtUp(entry, k, callerPkg, argsTerm, index, table);
	}

	/**
	 * Caught up with the cache: a sealed entry makes this reader a finished
	 * branch; otherwise it registers as a sleeper of {@code entry} — billed to
	 * its own coat's region — and parks, and a park that races a fresh answer
	 * keeps consuming instead of sleeping past data. This is the sleeper-edge
	 * bookkeeping completion detection reads (docs/design/table-completion.md).
	 */
	private static Fiber<Nothing> parkWhenCaughtUp(
			TableEntry<Object> entry,
			Fiber.Fn<Package, Nothing> k,
			Package callerPkg,
			Unifiable<?> argsTerm,
			int index,
			Table table) {
		if (entry.isComplete()) {
			// sealed ⇒ the answer count is FINAL. The caught-up check that led
			// here and this seal read are not atomic — an answer AND the seal can
			// both land between them — so re-check against the now-final count
			// and read any answers that slipped in; dying here one short would
			// silently lose them (the reader's owner then seals without them)
			if (index < entry.getAnswerCount()) {
				return Fiber.defer(() -> consume(entry, k, callerPkg, argsTerm, index, table));
			}
			// truly caught up at a sealed entry: the chain ends here, and the
			// mode decides what that means (a finished branch; or closed's
			// value replay). Racy read is safe: a stale false parks a dead
			// registration, which ledgers accept as sealed-parked
			return table.caughtUp(entry,
					new Registration(k, callerPkg, argsTerm, index, EnclosingCall.entryOf(callerPkg)));
		}
		// the parked state is callerPkg: its coat names the call this reader
		// belongs to — the registration's enclosingCall, as opposed to
		// {@code entry}, the call it waits for
		TableEntry<Object> enclosingCall = EnclosingCall.entryOf(callerPkg);
		Registration registration = new Registration(k, callerPkg, argsTerm, index, enclosingCall);
		if (enclosingCall != null) {
			// ledger first, then park: a respawn can only drain a parked
			// registration, so the sleeping record is always there to remove
			enclosingCall.getRegion().sleeping(registration, entry.getRegion());
		}
		if (entry.park(registration)) {
			// a park that completes the region seals it; the seal's emit fiber
			// (closed tabling) rides on as this branch's tail
			return enclosingCall != null
					? enclosingCall.getRegion().sealCascade()
					: done(nothing());
		}
		if (enclosingCall != null) {
			enclosingCall.getRegion().awake(registration);
		}
		// An answer arrived while registering — keep consuming
		return Fiber.defer(() -> consume(entry, k, callerPkg, argsTerm, index, table));
	}

}
