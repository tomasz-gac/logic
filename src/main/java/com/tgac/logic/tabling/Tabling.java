package com.tgac.logic.tabling;

// ABOUTME: Tabled evaluation of logic goals: answers are cached per call and shared,
// ABOUTME: which makes left-recursive and mutually recursive predicates terminate.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Barrier;
import com.tgac.logic.tabling.primitives.JoinMap;
import com.tgac.logic.tabling.primitives.Region;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
	 * 3. A new call becomes master and executes the body, caching answer terms
	 * 4. An existing call consumes cached answer terms, parking when it catches up
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
	 *   answerPkg   a body success, still wearing this call's coat; on call
	 *               EXIT (the answer's downstream is the CALLER's code) it
	 *               is re-coated back to the caller.
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
		return Barrier.priced(p -> tabledOrder(p, relation, argsTerm), callerPkg -> k -> {
			assertNoConstraints(callerPkg, "at a tabled call");
			return MiniKanren.reify(callerPkg.substitution(), argsTerm).flatMap(reifiedArgs -> {
				Call key = Call.of(relation, reifiedArgs);
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
					EnclosingCall callerCall = EnclosingCall.current(callerPkg);
					// the caller's running value at this call site, restored and
					// ⊗-combined with each answer on the way out; the body runs from
					// ONE so the cell stays caller-agnostic
					Object callerWeight = table.weightOf(callerPkg);
					Package bodyPkg = clearRecurrentIfWait(table, table.resetStore(table.resetWeight(callerPkg)))
						.putStore(new EnclosingCall(entry));
					if (table.isWaitMode()) {
						// at seal, replay THIS caller's continuation with the star's
						// folded value — the explore dropped every fragment
						entry.getRegion().onSealed(() ->
								emit(entry, k, callerPkg, argsTerm, callerCall, table));
					}
					return Region.track(entry.getRegion(),
							produce(entry, body.get(), bodyPkg, argsTerm, k, callerCall, callerWeight, table));
				}
				return consume(entry, k, callerPkg, argsTerm, 0, table);
			});
		});
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
	 * Tabling does not capture constraints yet: variant keys ignore
	 * constraint stores, and answers are cached as plain reified terms. A
	 * cache produced under one constraint context and consumed under another
	 * would silently generalize answers, so any active constraint store
	 * around a tabled call or answer is rejected loudly instead.
	 */
	private static void assertNoConstraints(Package pkg, String when) {
		pkg.getStores().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.filter(cs -> !cs.isEmpty())
				.findAny()
				.ifPresent(cs -> {
					throw new IllegalStateException(
							"Tabling does not capture constraints yet: non-empty "
									+ cs.getClass().getSimpleName() + " " + when);
				});
	}

	/**
	 * Master: execute the body with a caching hook in its continuation.
	 * Each new answer is cached, the consumers parked on the entry are
	 * respawned, and the answer flows on through the query. Duplicate
	 * answers fail their branch.
	 */
	private static Fiber<Nothing> produce(
			TableEntry<Object> entry,
			Goal goal,
			Package bodyPkg,
			Unifiable<?> argsTerm,
			Fiber.Fn<Package, Nothing> k,
			EnclosingCall callerCall,
			Object callerWeight,
			Table table) {
		return goal.apply(bodyPkg).apply(answerPkg -> {
			assertNoConstraints(answerPkg, "on a tabled answer");
			// the value this derivation carries from the call's inputs to the
			// answer — caller-agnostic, since the body ran from ONE
			Object value = table.weightOf(answerPkg);
			return MiniKanren.reify(answerPkg.substitution(), argsTerm).flatMap(answerTerm ->
					entry.addAnswer(captureIfWait(table, entry, answerPkg, answerTerm), value)
							.map(parked -> respawn(entry, parked, table)
									// detach-k: the answer's downstream is the CALLER's
									// code, not this body's — detaching it makes this
									// fiber's completion mean BODY EXHAUSTED, the event
									// the counters need. Control exits the body here,
									// so the answer is re-coated to the caller before k
									// runs: the downstream parks AND is billed as the
									// caller (a nested master's downstream can still
									// derive caller answers, so the caller must not
									// complete under it — table-completion.md §4). The
									// caller's running value is restored and ⊗ the answer.
									.flatMap(__ -> {
										Package base = table.withWeight(
												answerPkg.putStore(callerCall),
												table.times(callerWeight, value));
										// wait-mode escape: a pre-star fragment dropped at the closed collector (§4.1)
										Package callerAnswerPkg = table.isWaitMode()
												? base.putStore(Exploration.MARKER)
												: base;
										Fiber<Nothing> downstream = Fiber.defer(() ->
												k.apply(callerAnswerPkg));
										return Fiber.detach(
												Region.track(regionOf(callerCall.entry()), downstream));
									}))
							.getOrElse(() -> done(nothing())));
		});
	}

	/**
	 * Wait-mode emit (closed tabling): at seal, publish each answer to the master's
	 * caller with the star-folded value. Explore ran as presence tabling and the
	 * closed collector dropped every fragment; this replays the master's
	 * continuation {@code k} once per answer with {@code x = A* ⊗ b}, ⊗ the
	 * caller's running value (docs/design/star-tabling.md §4.5).
	 */
	private static Fiber<Nothing> emit(
			TableEntry<Object> entry,
			Fiber.Fn<Package, Nothing> k,
			Package callerPkg,
			Unifiable<?> argsTerm,
			EnclosingCall callerCall,
			Table table) {
		// the real value rides the SemiringStore, not the presence cell weight —
		// read the caller's value and write results through the closed accessors
		Table.ClosedMode mode = table.getClosedMode();
		Map<Reified<?>, Object> solved = mode.starSolve.apply(entry);
		Object callerValue = mode.storeReader.apply(callerPkg);
		Fiber<Nothing> result = done(nothing());
		for (int i = 0; i < entry.getAnswerCount(); i++) {
			Tuple2<Reified<?>, Object> answer = entry.getAnswerAt(i);
			Object x = solved.get(answer._1);
			if (x == null) {
				continue;
			}
			Reified<?> answerTerm = answer._1;
			Object value = mode.semiring.times(callerValue, x);
			result = result.flatMap(__ ->
					emitAnswer(k, callerPkg, argsTerm, callerCall, mode, answerTerm, value));
		}
		return result;
	}

	/**
	 * Publish one sealed answer to the caller: instantiate it, unify it against the
	 * call pattern to bind the caller's variables, re-coat to the caller and set the
	 * ⊗-combined star value on the SemiringStore, then hand it to {@code k}.
	 */
	private static Fiber<Nothing> emitAnswer(
			Fiber.Fn<Package, Nothing> k,
			Package callerPkg,
			Unifiable<?> argsTerm,
			EnclosingCall callerCall,
			Table.ClosedMode mode,
			Reified<?> answerTerm,
			Object value) {
		return MiniKanren.instantiate(answerTerm).flatMap(freshTerm ->
				MiniKanren.unify(callerPkg.substitution(), argsTerm.getObjectTerm(), freshTerm.getObjectTerm())
						.map(callerPkg::withSubstitutions)
						.map(pkg -> pkg.putStore(callerCall))
						.map(pkg -> mode.storeWriter.apply(pkg, value))
						.map(k::apply)
						.getOrElse(() -> done(nothing()))
						.flatMap(fib -> fib));
	}

	/**
	 * Respawn parked consumers as detached fibers so they pick up the answers
	 * cached since they parked. Whoever derives an answer drives its consequences.
	 */
	private static Fiber<Nothing> respawn(TableEntry<Object> entry, List<Registration> parked, Table table) {
		Fiber<Nothing> result = done(nothing());
		for (Registration r : parked) {
			TableEntry<?> enclosingCall = r.getEnclosingCall();
			if (enclosingCall != null) {
				enclosingCall.getRegion().awake(r);
			}
			Fiber<Nothing> consumer = Fiber.defer(() ->
					consume(entry, r.getContinuation(), r.getPkg(), r.getArgsTerm(), r.getNextIndex(), table));
			result = result.flatMap(__ -> Fiber.detach(Region.track(regionOf(enclosingCall), consumer)));
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
			Tuple2<Reified<?>, Object> answer = entry.getAnswerAt(index);
			Object cellValue = answer._2;
			// Fresh variables per consumption, so separate consumptions of the
			// same answer don't alias each other's free variables
			return MiniKanren.instantiate(answer._1).flatMap(freshTerm ->
					MiniKanren.unify(callerPkg.substitution(), argsTerm.getObjectTerm(), freshTerm.getObjectTerm())
							.map(callerPkg::withSubstitutions)
							.map(unifiedPkg -> markRecurrentIfWait(table, entry, unifiedPkg, answer._1))
							// ⊗ the answer's cell value into this consumer's running value
							.map(unifiedPkg -> table.scaleWeight(unifiedPkg, cellValue))
							.map(weightedPkg -> k.apply(weightedPkg)
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
			// sealed: no new answers can ever arrive — the caught-up reader is
			// a finished branch, not a sleeper (racy read is safe: a stale
			// false parks a dead registration, which ledgers accept as
			// sealed-parked)
			return done(nothing());
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
	/** Wait mode: a derivation that consumed a still-open call has looped — mark it. */
	private static Package markRecurrentIfWait(Table table, TableEntry<Object> entry, Package pkg, Reified<?> consumed) {
		if (!table.isWaitMode() || entry.isComplete()) {
			return pkg;
		}
		Recurrent prev = pkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		return pkg.putStore(prev.and(consumed));
	}

	/** Wait mode: clear the looping record at a master boundary — a fresh derivation. */
	private static Package clearRecurrentIfWait(Table table, Package pkg) {
		return table.isWaitMode() ? pkg.putStore(Recurrent.NONE) : pkg;
	}

	/**
	 * Wait mode: a NON-looping derivation (no Recurrent tag) is a base seed — grab its
	 * value off the SemiringStore and fold it onto the entry, before the dedup. Returns
	 * {@code answerTerm} so it slots into addAnswer as an identity with a side effect.
	 */
	private static Reified<?> captureIfWait(
			Table table, TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm) {
		if (!table.isWaitMode()) {
			return answerTerm;
		}
		Recurrent rec = answerPkg.getStores().get(Recurrent.class)
				.map(Recurrent.class::cast).getOrElse(Recurrent.NONE);
		Table.ClosedMode mode = table.getClosedMode();
		Object value = mode.storeReader.apply(answerPkg);
		if (rec.consumed.isEmpty()) {
			entry.addBase(answerTerm, value, mode.semiring);
		} else if (rec.consumed.size() == 1) {
			entry.addEdge(answerTerm, rec.consumed.head(), value, mode.semiring);
		} else {
			throw new IllegalStateException("nonlinear recursion: a derivation consumed "
					+ rec.consumed.size() + " looping calls; star handles only linear systems");
		}
		return answerTerm;
	}

}
