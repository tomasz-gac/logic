package com.tgac.logic.tabling;

// ABOUTME: Tabled evaluation of logic goals: answers are cached per call and shared,
// ABOUTME: which makes left-recursive and mutually recursive predicates terminate.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Emitter;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.primitives.JoinMap;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
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
import io.vavr.collection.Map;
import java.util.ArrayList;
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
 * applications consume the cache. A consumer that exhausts the cache awaits
 * the entry's channel — the live frame parks, growth wakes it with the grown
 * log, and the entry's seal completes it with the final one. A consumer
 * completed at its cursor is a finished branch.
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
	 * STATE FOLLOWS THE DATA, EXECUTION FOLLOWS THE FRAME:
	 *
	 *   callerPkg   the state at this call site.
	 *   bodyPkg     callerPkg re-based for the body on call ENTRY (stores
	 *               stripped to the key, mode state reset).
	 *   answerPkg   a body success; it ENDS at the answer cell. Answers
	 *               reach callers only through their consumers, each
	 *               running under its own caller's state.
	 *
	 * Billing needs no package-level tracking: the body runs as the entry's
	 * workforce (the frame's ambient scope), an inner call's consumer runs
	 * inside the body's frames and inherits it, and its park leaves the
	 * blocked record completion detection reads.
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
					Reader reader = Reader.of(k, callerPkg, argsTerm);
					Table table = reader.getTable();
					// a weighted solve whose semiring cannot table (non-idempotent,
					// non-closed) would silently drop weights here — refuse loudly
					table.assertTablingAllowed();
					// subsumptive reuse: a sealed general entry is a read-only relation
					// containing every answer this instance call could produce (subset
					// property) — read it through consume's unification filter
					TableEntry<Object> subsumer = table.reusableSubsumer(key);
					if (subsumer != null) {
						return consume(subsumer, reader, subsumer.answers());
					}
					TableEntry<Object> entry = table.getOrCreateEntry(key);
					// the ANONYMOUS MASTER, selected by the claim-once CAS
					// (Fiber.tryProduceTo): the body runs as this entry's
					// workforce and belongs to no caller; production is the
					// emitter, so billing and production cannot disagree.
					// Every caller, the first included, reads the cell as a
					// consumer; the sleeper it parks is the dependency edge
					// completion detection needs, so a caller can never seal
					// ahead of a call it depends on. The body runs FROM THE
					// KEY: the first caller's constraint stores are stripped
					// and the key's residues restated, so the cache holds
					// exactly the region the key names — every caller filters
					// at consumption by its own state
					return Fiber.produce(entry.channel(), emit -> {
								// the key cannot represent a parked suspension and the
								// caller-agnostic body must not inherit one — refuse loudly;
								// consuming an existing entry under one stays legal (the
								// caller's copy ripens through its own chokepoint)
								if (Propagation.suspensionsPending(callerPkg)) {
									throw new IllegalStateException(
											"a tabled call cannot become master under parked suspensions: "
													+ "the call key cannot see them and the body must not inherit them");
								}
								Package bodyPkg = stripConstraints(table.bodyState(callerPkg));
								Goal seeded = projection.seed(body.get());
								return produce(entry, seeded, bodyPkg, argsTerm, table, emit);
							})
							.flatMap(__ -> consume(entry, reader, entry.answers()));
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

	/**
	 * The caller's constraint knowledge about the call vars, projected per
	 * store: the canonical (hole-named) key citizens that join the
	 * {@link Call}, and the restate goals that seed the master's body with
	 * exactly that knowledge — the key renamed back onto the call vars and
	 * stated. A store that cannot project cannot enter the key, and unkeyed
	 * knowledge means silently wrong reuse — refused loudly. An EMPTY
	 * projection (nothing known about the call vars) stays out of the key,
	 * so calls under irrelevant knowledge stay constraint-free variants;
	 * caller-private knowledge is split away — sound by containment,
	 * filtered at consumption.
	 */
	@Value
	private static class Projection {
		Map<Class<?>, Object> residues;
		java.util.List<Goal> restates;

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
				Projectable<?> keyed = ((Projectable<?>) store).project(callVars);
				if (!keyed.isEmpty()) {
					residues = residues.put(store.getClass(), keyed);
					restates.add(Propagation.absorb(keyed.rename(Renaming.ofSlots(targets))));
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

	/**
	 * The delivery unification through the public entry — a helper so the
	 * two-Unifiable overload resolves (with {@code T = Object} the
	 * {@code (Unifiable<T>, T)} overload would be applicable too).
	 */
	private static <T> Goal unifyArgs(Unifiable<T> args, Unifiable<T> instantiated) {
		return Constraints.unify(args, instantiated);
	}

	/**
	 * Every answer factor renamed through ONE shared mint and re-stated —
	 * seeded holes go to the instantiation's fresh vars, everything else
	 * (body locals) mints fresh per delivery: the existential. Ground answers
	 * have no factors and the goal is success.
	 */
	private static Goal restateAll(AnswerKey key, java.util.List<LVar<?>> freshHoles) {
		if (key.getResidues().isEmpty()) {
			return Goal.success();
		}
		java.util.Map<LVar<?>, Term<?>> seed = new java.util.HashMap<>();
		java.util.List<LVar<?>> holeVars = key.getHoleVars();
		for (int i = 0; i < holeVars.size(); i++) {
			seed.put(holeVars.get(i), freshHoles.get(i));
		}
		Renaming mint = Renaming.into(seed);
		Goal seeded = Goal.success();
		for (Tuple2<Class<?>, Object> entry : key.getResidues()) {
			seeded = Conjunction.of(seeded,
					Propagation.absorb(((Projectable<?>) entry._2).rename(mint)));
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
	 * An answer's residues: each store's factor normalized against the
	 * answer's substitutions (spent entries drop — the ground-answer fast
	 * path is a factor that normalizes to empty). The WHOLE delta rides —
	 * body locals and their couplings included, replayed as existentials at
	 * consumption and verified by the consumer's labelling. Non-projectable
	 * live knowledge refuses, and constrained answers under a mode that
	 * cannot replay them (closed) refuse before caching.
	 */
	private static Map<Class<?>, Object> answerResidues(Package answerPkg, Table table) {
		Map<Class<?>, Object> residues = HashMap.empty();
		Renaming normalization = Renaming.walking(answerPkg.substitution());
		for (Packaged store : answerPkg.getStores().values()) {
			if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
				continue;
			}
			if (!(store instanceof Projectable)) {
				throw new IllegalStateException(
						"Tabling does not support non-projectable store: non-empty "
								+ store.getClass().getSimpleName() + " on a tabled answer");
			}
			Projectable<?> normalized = ((Projectable<?>) store).rename(normalization);
			if (!normalized.isEmpty()) {
				residues = residues.put(store.getClass(), normalized);
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
	 * answer is cached and emitted — growth wakes the consumers parked at
	 * the entry's channel — and the answer reaches callers only through
	 * their consumers, so this fiber's completion means BODY EXHAUSTED, the
	 * event the counters need. Duplicate answers fail their branch.
	 */
	private static Fiber<Nothing> produce(
			TableEntry<Object> entry,
			Goal goal,
			Package bodyPkg,
			Unifiable<?> argsTerm,
			Table table,
			Emitter<JoinMap<AnswerKey, Object>> emit) {
		return goal.apply(bodyPkg).apply(answerPkg -> {
			// the Table transport is the canary: a goal that returned a fresh
			// package instead of deriving from its input shed every store — the
			// damage downstream is SILENT (answers reified over fresh
			// substitutions cache over-general) — so refuse loudly here
			if (answerPkg.getStores().get(Table.class).getOrElse((Packaged) null) != table) {
				throw new IllegalStateException(
						"a goal inside a tabled body dropped its stores: packages must be "
								+ "derived from the incoming one, never minted fresh "
								+ "(the body's Table transport is missing or foreign)");
			}
			// a parked suspension is a condition the answer still owes; the
			// AnswerKey cannot carry it, so caching now would drop the debt
			if (Propagation.suspensionsPending(answerPkg)) {
				throw new IllegalStateException(
						"an answer may not leave a tabled body while suspensions pend: "
								+ "the owed condition cannot ride the answer");
			}
			return MiniKanren.reifyWithHoles(answerPkg.substitution(), argsTerm.getObjectTerm())
					.flatMap(reified -> {
						Map<Class<?>, Object> residues = answerResidues(answerPkg, table);
						// what the cell caches: the term and the value this derivation
						// carries — caller-agnostic, since the body ran from ONE
						Tuple2<Reified<?>, Object> cached = table.capture(entry, answerPkg, reified._1);
						// production is the emit: the fold FEEDS the parked
						// consumers as this producer's tail; an absorbed
						// (duplicate) answer is an inert join, an entailed one
						// has no delta at all
						return entry.answerDelta(AnswerKey.of(cached._1, reified._2, residues), cached._2)
								.map(emit::emit)
								.getOrElse(Fiber.done(Nothing.nothing()));
					});
		});
	}

	/**
	 * Consumer: unify instantiated cached answers with the argument tuple,
	 * yielding each success to the continuation. On catching up with the
	 * cache the consumer awaits the entry's channel: growth wakes the live
	 * frame with the grown log, and the seal completes it at its cursor —
	 * the mode decides what that honest end means.
	 *
	 * @param answers the snapshot the reader's cursor indexes into — a
	 * 		consumption starts from the answers as of the call; every later
	 * 		value arrives with the wake, never polled
	 */
	static Fiber<Nothing> consume(TableEntry<Object> entry, Reader reader, JoinMap<AnswerKey, Object> answers) {
		Fiber.Fn<Package, Nothing> k = reader.getContinuation();
		Package callerPkg = reader.getPkg();
		Unifiable<?> argsTerm = reader.getArgsTerm();

		if (reader.getNextIndex() < answers.size()) {
			Tuple2<AnswerKey, Object> answer = answers.get(reader.getNextIndex());
			AnswerKey key = answer._1;
			Object cellValue = answer._2;
			// Fresh variables per consumption, so separate consumptions of the
			// same answer don't alias each other's free variables. Delivery is
			// a goal: unify the caller's args with the instantiation — through
			// the public entry, so the caller's stores revise — then RESTATE
			// the residues onto the fresh holes. The meet-at-consumption: a
			// failed unification, a violated store or a violated residue all
			// silently fail the delivery and consumption moves on
			return MiniKanren.instantiateWithHoles(key.getTerm()).flatMap(inst ->
					Conjunction.of(
									unifyArgs(argsTerm.getObjectUnifiable(), inst._1.getObjectUnifiable()),
									restateAll(key, inst._2))
							.apply(callerPkg)
							// streaming ⊗s the cell value in; closed records the loop
							.apply(constrainedPkg -> k.apply(reader.getTable().absorb(constrainedPkg,
									entry, key.getTerm(), cellValue)))
							.flatMap(__ -> Fiber.defer(() ->
									consume(entry, reader.advanced(), answers))));
		}

		// caught up: await the cell. The suspend is atomic with growth and
		// seal, so every race lands in one of the two arms: more — answers
		// grew past the cursor, keep consuming the handed value (a sealed
		// result past the cursor is the final tail — same arm); sealed at
		// the cursor — the chain honestly ends, and the mode decides what
		// that means (a finished branch; or closed's value replay). The
		// frame's ambient scope records the wait — the sleeper-edge
		// bookkeeping completion detection reads (docs/design/table-completion.md)
		return Fiber.await(entry.channel(), v -> v.size() > reader.getNextIndex())
				.flatMap(r -> {
					if (r.getValue().size() > reader.getNextIndex()) {
						return Fiber.defer(() -> consume(entry, reader, r.getValue()));
					}
					// progress-free completions can only be seals: a more() is
					// only ever completed past the predicate. The mode's caught-up
					// work (closed's SOLVE) requires the seal - enforce, loudly
					if (!r.isSealed()) {
						throw new IllegalStateException(
								"await completed without progress on an unsealed entry: " + entry);
					}
					return reader.getTable().caughtUp(entry, reader);
				});
	}

}
