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
import java.util.ArrayDeque;
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
				if (entry.tryBecomeMaster()) {
					return produce(entry, body.get(),
							pkg.putStore(new Producer(entry)), argsTerm, k,
							Producer.current(pkg))
							.flatMap(__ -> {
								entry.workFinished();
								tryComplete(entry);
								return done(nothing());
							});
				}
				return consume(entry, k, pkg, argsTerm, 0);
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
			Fiber.Fn<Package, Nothing> k,
			Producer caller) {
		return goal.apply(initialPkg).apply(answerPkg -> {
			assertNoConstraints(answerPkg, "on a tabled answer");
			return MiniKanren.reify(answerPkg.substitution(), argsTerm).flatMap(answerTerm ->
					entry.addAnswer(answerTerm)
							.map(parked -> respawn(entry, parked)
									// detach-k: the answer's downstream is the CALLER's
									// work, not this production's — detaching it makes
									// this fiber's completion mean BODY EXHAUSTED, the
									// event the counters need. It runs under the
									// caller's tag AND counts as the caller's live work:
									// a nested master's downstream can still derive
									// caller answers, so the caller must not complete
									// under it (table-completion.md §4).
									.flatMap(__ -> {
										TableEntry callerEntry = caller.entry();
										Fiber<Nothing> downstream = Fiber.defer(() ->
												k.apply(answerPkg.putStore(caller)));
										if (callerEntry == null) {
											return Fiber.detach(downstream);
										}
										callerEntry.workStarted();
										return Fiber.detach(downstream.flatMap(___ -> {
											callerEntry.workFinished();
											tryComplete(callerEntry);
											return done(nothing());
										}));
									}))
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
			TableEntry producer = Producer.of(r.getPkg());
			if (producer != null) {
				producer.removeOutpost(r);
				producer.workStarted();
			}
			Fiber<Nothing> consumer = Fiber.defer(() ->
					consume(entry, r.getContinuation(), r.getPkg(), r.getArgsTerm(), r.getNextIndex()));
			Fiber<Nothing> counted = producer == null ? consumer :
					consumer.flatMap(__ -> {
						producer.workFinished();
						tryComplete(producer);
						return done(nothing());
					});
			result = result.flatMap(__ -> Fiber.detach(counted));
		}
		return result;
	}

	/**
	 * The completion cascade: try the entry's self-SCC rule; when it fires,
	 * the registrations that were parked there are dead, and each one's
	 * PRODUCER may now satisfy its own rule ("parks on a complete entry") —
	 * DAG dependencies complete bottom-up. Monitors are never nested: the
	 * rule runs under one entry's lock, the cascade walks outside it.
	 */
	static void tryComplete(TableEntry entry) {
		ArrayDeque<TableEntry> queue = new ArrayDeque<>();
		queue.add(entry);
		while (!queue.isEmpty()) {
			List<Registration> dead = queue.poll().tryCompleteHere();
			if (dead == null) {
				continue;
			}
			for (Registration r : dead) {
				TableEntry producer = Producer.of(r.getPkg());
				if (producer != null) {
					producer.removeOutpost(r);
					queue.add(producer);
				}
			}
		}
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

		Registration registration = new Registration(k, initialPkg, argsTerm, index);
		TableEntry producer = Producer.of(initialPkg);
		if (producer != null) {
			// outpost first, then park: a respawn can only drain a parked
			// registration, so the outpost is always there for it to remove
			producer.addOutpost(registration, entry);
		}
		if (entry.register(registration)) {
			if (producer != null) {
				tryComplete(producer);
			}
			return done(nothing());
		}
		if (producer != null) {
			producer.removeOutpost(registration);
		}

		// An answer arrived while registering — keep consuming
		return Fiber.defer(() -> consume(entry, k, initialPkg, argsTerm, index));
	}
}
