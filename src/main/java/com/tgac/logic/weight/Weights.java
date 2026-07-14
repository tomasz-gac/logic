package com.tgac.logic.weight;

// ABOUTME: The weighted-inference front door: factor injects a per-choice weight
// ABOUTME: (⊗ into the running store), solve ⊕-folds the per-answer stores to a total.

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.Scheduler;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.tabling.Table;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
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
		Package root = Package.empty().withStore(Table.empty()).withStore(product.one());
		Queue<SemiringStore> perAnswer = new ConcurrentLinkedQueue<>();

		Fiber<Nothing> recur = goal.apply(root).run(s -> {
			perAnswer.add(s.getStore(SemiringStore.class));
			return nothing();
		});
		Scheduler<Nothing> scheduler = factory.apply(recur);
		try {
			while (!scheduler.run(64, v -> {
			})) {
				// drain the search to completion
			}
		} finally {
			try {
				scheduler.close();
			} catch (Exception e) {
				throw new RuntimeException("Failed to close engine", e);
			}
		}

		SemiringStore total = product.zero();
		for (SemiringStore s : perAnswer) {
			total = product.plus(total, s);
		}
		return total;
	}
}
