package com.tgac.logic.finitedomain;

// ABOUTME: Pins the SEARCH COST of a fork-heavy bounded solve as a step budget -
// ABOUTME: driver ordering changes that explode exploration fail here, wall-free.

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.multo;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.interpreter.StepListener;
import com.tgac.functional.fibers.schedulers.UnfairBreadthFirstScheduler;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class SearchCostPinTest {

	/**
	 * The MultiplicationTest.shouldNotMultiplyWithoutDomain shape, scaled
	 * down: a propagation-heavy search whose exploration COST is sensitive
	 * to driver ordering (the bucket-structure revert, 6cce163, was a 4x
	 * wall regression the correctness suite could not see). The budget is
	 * ~2x the observed step count under the default driver - a driver
	 * change that trips it has changed the SHAPE of the search, not just
	 * its bookkeeping.
	 */
	@Test
	public void aForkHeavyBoundedSearchStaysWithinItsStepBudget() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();
		AtomicLong steps = new AtomicLong();
		StepListener counting = new StepListener() {
			@Override
			public void onStep(Fiber<?> computation) {
				steps.incrementAndGet();
			}
		};

		long solutions = multo(a, b, c)
				.and(dom(a, Interval.of(0, 12)))
				.and(dom(b, Interval.of(0, 12)))
				.and(dom(c, Interval.of(0, 144)))
				.solve(lval(Tuple.of(a, b, c)),
						fiber -> new UnfairBreadthFirstScheduler<>(fiber).withListener(counting))
				.count();

		assertThat(solutions).isGreaterThan(0);
		// observed 178,870 steps under the unfair driver (deterministic);
		// the budget is ~2x - tripping it means the search SHAPE changed
		assertThat(steps.get()).isLessThan(360_000);
	}
}
