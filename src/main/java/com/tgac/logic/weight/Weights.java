package com.tgac.logic.weight;

// ABOUTME: The weighted-inference front door: factor injects a per-choice weight
// ABOUTME: (⊗ into the running store), solve ⊕-folds the per-answer stores to a total.

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.Scheduler;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.tabling.Exploration;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Deque;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Weights {

	/**
	 * Succeeds once, multiplying {@code weight} into {@code ring}'s running value
	 * in the ambient {@link SemiringStore}. ⊗ = along a derivation; because the
	 * package is immutable, branches carry independent weights. Inert when no
	 * store rides the package (weighing off), like a tracer-less named goal.
	 */
	public static <S> Goal factor(Semiring<S> ring, S weight) {
		return Bounded.of(1, Goal.goal(s -> Cont.just(
						s.updateStore(SemiringStore.class,
								store -> store.with(ring, ring.times(store.get(ring), weight)))))
				.named("factor"));
	}

	/**
	 * Runs {@code goal} to exhaustion under the product semiring, ⊕-folding the
	 * per-answer stores into one total. The ring set is fixed here (the seed is
	 * {@code product.one()}), so every store in flight is complete and
	 * {@link #factor} only ever updates an existing entry. Eager, like counting:
	 * a weighted total is unknown until the search completes.
	 */
	public static SemiringStore solve(Goal goal, Semiring<SemiringStore> product,
			Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		Queue<SemiringStore> perAnswer = new ConcurrentLinkedQueue<>();
		runToCompletion(goal.apply(seed(product)).run(s -> {
			perAnswer.add(s.getStore(SemiringStore.class));
			return nothing();
		}), factory);

		SemiringStore total = product.zero();
		for (SemiringStore s : perAnswer) {
			total = product.plus(total, s);
		}
		return total;
	}

	/**
	 * Each answer paired with its own branch's weight — the per-answer view the
	 * fold in {@link #solve} discards. Lets a caller ask which branch is
	 * cheapest, not just the folded extremum. Eager, like {@link #solve}.
	 */
	public static <T> Stream<Tuple2<Reified<T>, SemiringStore>> solveEach(Goal goal, Unifiable<T> out,
			Semiring<SemiringStore> product, Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		return lazily(sink -> goal.apply(seed(product))
				.flatMap(s -> Constraints.reify(s, out)
						.map(answer -> Tuple.of(answer, s.getStore(SemiringStore.class))))
				.run(pair -> {
					sink.accept(pair);
					return nothing();
				}), factory);
	}

	/**
	 * Weighted solve with STREAMING tabling: tabled calls thread their weights,
	 * the answer cell folds by ⊕, and recursion (cyclic included) terminates
	 * because boundedness (a* = 1) makes re-derivation stationary — min-plus,
	 * Viterbi, boolean. The sibling {@code solveClosed} (star) is the escape for
	 * the unbounded-or-non-idempotent semirings (probability, provenance);
	 * {@link #solveEach} without a capability is the plain non-tabling per-answer
	 * solve. Naming the capability at the call site keeps the choice of strategy
	 * explicit. With no tabled goal the weighted table simply sits unused.
	 */
	public static <T> Stream<Tuple2<Reified<T>, SemiringStore>> solveBounded(Goal goal, Unifiable<T> out,
			BoundedSemiring<SemiringStore> product, Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		Package root = Package.empty().withStore(weightedTable(product)).withStore(product.one());
		return lazily(sink -> goal.apply(root)
				.flatMap(s -> Constraints.reify(s, out)
						.map(answer -> Tuple.of(answer, s.getStore(SemiringStore.class))))
				.run(pair -> {
					sink.accept(pair);
					return nothing();
				}), factory);
	}

	/** A table whose cell folds by {@code product} and whose running value is the SemiringStore. */
	@SuppressWarnings("unchecked")
	private static Table weightedTable(IdempotentSemiring<SemiringStore> product) {
		return Table.weighted(
				(IdempotentSemiring<Object>) (IdempotentSemiring<?>) product,
				p -> p.getStores().get(SemiringStore.class).getOrElse(product.one()),
				(p, v) -> p.putStore((SemiringStore) v));
	}

	/**
	 * Weighted solve with CLOSED (star) tabling: a closed but non-idempotent (or
	 * unbounded) semiring whose values cannot stream. Explore runs as plain
	 * presence tabling; the real value is deferred and dropped as an exploration
	 * fragment, then summed by the star at each SCC seal and emitted. (Emit lands
	 * in a later phase; today it explores and drops.) A bounded semiring is a
	 * legal argument too -- you pay O(n^3) for the degenerate a* = 1.
	 */
	public static <T> Stream<Tuple2<Reified<T>, SemiringStore>> solveClosed(Goal goal, Unifiable<T> out,
			ClosedSemiring<SemiringStore> ring, Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		Package root = Package.empty()
				.withStore(closedTable(ring))
				.withStore(ring.one());
		return lazily(sink -> goal.apply(root)
				.flatMap(s -> Constraints.reify(s, out)
						.map(answer -> Tuple.of(answer,
								s.getStore(SemiringStore.class),
								s.getStores().get(Exploration.class).isDefined())))
				.run(triple -> {
					// keep only finalized (untagged) answers; exploration fragments drop
					if (!triple._3) {
						sink.accept(Tuple.of(triple._1, triple._2));
					}
					return nothing();
				}), factory);
	}

	/** A closed table: presence cell for explore, the star solved and emitted per SCC seal. */
	private static Table closedTable(ClosedSemiring<SemiringStore> ring) {
		return Table.of(new Closed(ring));
	}

	private static Package seed(Semiring<SemiringStore> product) {
		return Package.empty()
				.withStore(Table.refusingTabling(
						"weighted tabling needs solveBounded (or solveClosed); "
								+ "solve/solveEach do not thread weights through tabled calls"))
				.withStore(product.one());
	}

	/**
	 * A lazy answer stream over the search: the scheduler is driven in batches only
	 * as the consumer pulls, so {@code limit(n)} runs the engine just far enough.
	 * The pipeline wires a result sink into the goal's continuation; each pulled
	 * element steps the engine until one arrives (mirrors {@link Goal#solveFrom}).
	 */
	private static <R> Stream<R> lazily(
			Function<Consumer<R>, Fiber<Nothing>> pipeline,
			Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		Deque<R> results = new LinkedBlockingDeque<>();
		Scheduler<Nothing> scheduler = factory.apply(pipeline.apply(results::add));
		Spliterator<R> spliterator = new Spliterator<R>() {
			@Override
			public boolean tryAdvance(Consumer<? super R> action) {
				while (results.isEmpty()) {
					if (scheduler.run(64, v -> {
					})) {
						while (!results.isEmpty()) {
							action.accept(results.poll());
						}
						return false;
					}
					if (!results.isEmpty()) {
						break;
					}
				}
				action.accept(results.poll());
				return true;
			}

			@Override
			public Spliterator<R> trySplit() {
				return null;
			}

			@Override
			public long estimateSize() {
				return Long.MAX_VALUE;
			}

			@Override
			public int characteristics() {
				return Spliterator.ORDERED | Spliterator.NONNULL;
			}
		};
		return StreamSupport.stream(spliterator, false)
				.onClose(() -> {
					try {
						scheduler.close();
					} catch (Exception e) {
						throw new RuntimeException("Failed to close engine", e);
					}
				});
	}

	@SuppressWarnings("StatementWithEmptyBody")
	private static void runToCompletion(Fiber<Nothing> recur,
			Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		try (Scheduler<Nothing> scheduler = factory.apply(recur)) {
			while (!scheduler.run(64, v -> {
			})) {
				// drain the search to completion
			}
		} catch (RuntimeException e) {
			// a goal threw during the search (e.g. a tabling guard) — propagate as-is
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Failed to close engine", e);
		}
	}
}
