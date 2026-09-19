package org.clauseway.logic.finitedomain;

// ABOUTME: Pins the SEARCH COST of a fork-heavy bounded solve as a step budget -
// ABOUTME: driver ordering changes that explode exploration fail here, wall-free.

import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.interpreter.Scope;
import org.clauseway.functional.fibers.interpreter.StepListener;
import org.clauseway.functional.fibers.schedulers.UnfairBreadthFirstScheduler;
import org.clauseway.logic.unification.Unifiable;
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
			public void onStep(Fiber<?> computation, Scope scope, String name) {
				steps.incrementAndGet();
			}
		};

		long solutions = Ints.multo(a, b, c)
				.and(dom(a, Ints.interval(0, 12)))
				.and(dom(b, Ints.interval(0, 12)))
				.and(dom(c, Ints.interval(0, 144)))
				.solve(lval(Tuple.of(a, b, c)),
						fiber -> new UnfairBreadthFirstScheduler<>(fiber).withListener(counting))
				.count();

		assertThat(solutions).isGreaterThan(0);
		// observed 178,870 steps under the unfair driver (deterministic);
		// the budget is ~2x - tripping it means the search SHAPE changed
		assertThat(steps.get()).isLessThan(360_000);
	}
}
