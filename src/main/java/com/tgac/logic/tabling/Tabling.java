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
import com.tgac.logic.tabling.TableEntry.Registration;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
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
	static <T> Goal tabled(Tabled<T> relation, T args, Supplier<Goal> body) {
		Unifiable<T> argsTerm = lval(args);
		// keyed widening: the call pattern is the table key, so no optimizer may
		// move binders across it — the contract as a type, not an accident of opacity
		return Barrier.priced(p -> tabledOrder(p, relation, argsTerm), pkg -> k -> {
			assertNoConstraints(pkg, "at a tabled call");
			return MiniKanren.reify(pkg.substitution(), argsTerm).flatMap(reifiedArgs -> {
				Call key = Call.of(relation, reifiedArgs);
				Table table = pkg.getStore(Table.class);
				TableEntry entry = table.getOrCreateEntry(key);
				return entry.tryBecomeMaster() ?
						produce(entry, body.get(), pkg, argsTerm, k) :
						consume(entry, k, pkg, argsTerm, 0);
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
	private static <T> long tabledOrder(Package p, Tabled<T> relation, Unifiable<T> argsTerm) {
		return p.getStores().get(Table.class)
				.map(Table.class::cast)
				.map(table -> table.getEntry(Call.of(relation,
						MiniKanren.reify(p.substitution(), argsTerm).get())))
				.filter(entry -> entry != null && entry.isComplete())
				.map(entry -> (long) entry.getAnswerCount())
				.getOrElse(Long.MAX_VALUE);
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
	private static <T> Fiber<Nothing> produce(
			TableEntry entry,
			Goal goal,
			Package initialPkg,
			Unifiable<T> argsTerm,
			Fiber.Fn<Package, Nothing> k) {
		return goal.apply(initialPkg).apply(answerPkg -> {
			assertNoConstraints(answerPkg, "on a tabled answer");
			return MiniKanren.reify(answerPkg.substitution(), argsTerm).flatMap(answerTerm ->
					entry.addAnswer(answerTerm)
							.map(parked -> respawn(entry, parked)
									.flatMap(__ -> k.apply(answerPkg)))
							.getOrElse(() -> done(nothing())));
		});
	}

	/**
	 * Respawn parked consumers as detached fibers so they pick up the answers
	 * cached since they parked. Whoever derives an answer drives its consequences.
	 */
	private static Fiber<Nothing> respawn(TableEntry entry, List<Registration> parked) {
		Fiber<Nothing> result = done(nothing());
		for (Registration r : parked) {
			result = result.flatMap(__ -> Fiber.detach(Fiber.defer(() ->
					consume(entry, r.getContinuation(), r.getPkg(), r.getArgsTerm(), r.getNextIndex()))));
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
			TableEntry entry,
			Fiber.Fn<Package, Nothing> k,
			Package initialPkg,
			Unifiable<?> argsTerm,
			int index) {

		if (index < entry.getAnswerCount()) {
			Reified<?> answerTerm = entry.getAnswerAt(index);
			// Fresh variables per consumption, so separate consumptions of the
			// same answer don't alias each other's free variables
			return MiniKanren.instantiate(answerTerm).flatMap(freshTerm ->
					MiniKanren.unify(initialPkg.substitution(), argsTerm.getObjectTerm(), freshTerm.getObjectTerm())
							.map(initialPkg::withSubstitutions)
							.map(unifiedPkg -> k.apply(unifiedPkg)
									.flatMap(__ -> Fiber.defer(() ->
											consume(entry, k, initialPkg, argsTerm, index + 1))))
							.getOrElse(() -> Fiber.defer(() ->
									consume(entry, k, initialPkg, argsTerm, index + 1)))
							.flatMap(fib -> fib));
		}

		if (entry.register(new Registration(k, initialPkg, argsTerm, index))) {
			return done(nothing());
		}

		// An answer arrived while registering — keep consuming
		return Fiber.defer(() -> consume(entry, k, initialPkg, argsTerm, index));
	}
}
