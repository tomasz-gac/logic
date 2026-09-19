package org.clauseway.logic;

// ABOUTME: Step-count pins per vision workload: exact reduction counts under the
// ABOUTME: deterministic BFS driver — a changed count is a decision, not drift.

import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.interpreter.Scope;
import org.clauseway.functional.fibers.interpreter.StepListener;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Logic;
import org.clauseway.logic.tabling.Tabled;
import org.clauseway.logic.tabling.Tabling;
import org.clauseway.logic.unification.LList;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * One pinned reduction count per engine lane, measured under the
 * deterministic {@link BreadthFirstScheduler} (the pins deliberately bypass
 * the chaos seam — counts are the assertion, order-independence is the other
 * suite's job). A failing pin is not a bug by itself: it is the engine
 * asking whether the cost change was intended. Re-measure, then either fix
 * the regression or re-pin with the change that bought it.
 */
public class StepCountPinsTest {

	private static <T> long steps(Goal goal, Unifiable<T> out) {
		AtomicLong count = new AtomicLong();
		StepListener counting = new StepListener() {
			@Override
			public void onStep(Fiber<?> node, Scope scope, String name) {
				count.incrementAndGet();
			}
		};
		goal.solve(out, fiber -> new BreadthFirstScheduler<>(fiber).withListener(counting))
				.collect(Collectors.toList());
		return count.get();
	}

	private static Domain<Integer> dom(int... values) {
		return Ints.enumerated(Arrays.stream(values).boxed().toArray(Integer[]::new));
	}

	/** Pure relational lane: every split of a six-element list. */
	@Test
	public void appendoEnumeratesAllSplits() {
		Unifiable<LList<Integer>> front = lvar();
		Unifiable<LList<Integer>> back = lvar();
		Unifiable<LList<Integer>> both = lvar();
		Goal splits = unify(both, LList.ofAll(1, 2, 3, 4, 5, 6))
				.and(Logic.appendo(front, back, both));

		assertThat(steps(splits, both)).isEqualTo(1_149);
	}

	/** Disequality lane: ordered distinct pairs from a five-element menu. */
	@Test
	public void distinctPairsUnderSeparateness() {
		Unifiable<LList<Integer>> menu = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Goal pairs = unify(menu, LList.ofAll(1, 2, 3, 4, 5))
				.and(exclude(a.unifies(b)))
				.and(Logic.membero(a, menu))
				.and(Logic.membero(b, menu));

		assertThat(steps(pairs, a)).isEqualTo(1_210);
	}

	/** FD lane: propagation and labelling through a ring step. */
	@Test
	public void ringStepsUnderDomains() {
		Domain<Integer> rooms = dom(1, 2, 3, 4, 5);
		Unifiable<Integer> from = lvar();
		Unifiable<Integer> to = lvar();
		Goal doors = FiniteDomain.dom(from, rooms)
				.and(FiniteDomain.dom(to, rooms))
				.and(Ints.addo(from, lval(1), to)
						.or(unify(from, lval(5)).and(unify(to, lval(1)))));

		assertThat(steps(doors, from)).isEqualTo(153);
	}

	/** Tabling + TCLP lane: the ring closure — recursion under live domains. */
	@Test
	public void tabledRingClosure() {
		Domain<Integer> rooms = dom(1, 2, 3, 4, 5);
		Tabled<Tuple1<Unifiable<Integer>>> reachable =
				Tabling.defineRecursive(self -> args -> args.apply(room ->
						unify(room, lval(5))
								.or(Logic.<Integer> exist(prev ->
										FiniteDomain.dom(prev, rooms)
												.and(FiniteDomain.dom(room, rooms))
												.and(Goal.defer(() -> self.apply(Tuple.of(prev))))
												.and(Ints.addo(prev, lval(1), room)
														.or(unify(prev, lval(5)).and(unify(room, lval(1)))))))));
		Unifiable<Integer> room = lvar();

		assertThat(steps(reachable.apply(Tuple.of(room)), room)).isEqualTo(601);
	}
}
