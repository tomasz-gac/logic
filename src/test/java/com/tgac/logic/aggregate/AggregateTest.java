package com.tgac.logic.aggregate;

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class AggregateTest {

	private Goal oneTwoThree(Unifiable<Integer> x) {
		return x.unifies(1).or(x.unifies(2)).or(x.unifies(3));
	}

	@Test
	public void findallCollectsEverySolution() {
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall(x, oneTwoThree(x), result);

		List<Integer> list = g.solve(result).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());
		assertThat(list).containsExactlyInAnyOrder(1, 2, 3);
	}

	@Test
	public void findallSucceedsExactlyOnce() {
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall(x, oneTwoThree(x), result);

		assertThat(g.solve(result).count()).isEqualTo(1);
	}

	@Test
	public void findallOfAFailingGoalIsTheEmptyList() {
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall(x, x.unifies(1).and(x.unifies(2)), result);

		List<Integer> list = g.solve(result).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());
		assertThat(list).isEmpty();
	}

	@Test
	public void findallDoesNotLeakTheTemplateBindingOutward() {
		// the collected copies are independent; x stays usable after
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall(x, oneTwoThree(x), result)
				.and(x.unifies(99));

		Integer bound = g.solve(x).findFirst().get().get();
		assertThat(bound).isEqualTo(99);
	}

	@Test
	public void countCountsSolutions() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count(oneTwoThree(x), n).solve(n).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void countOfAFailingGoalIsZero() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count(x.unifies(1).and(x.unifies(2)), n)
				.solve(n).findFirst().get().get();

		assertThat(result).isZero();
	}

	@Test
	public void sumAddsTheExpression() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> total = lvar();

		int result = Aggregate.sum(x, oneTwoThree(x), total).solve(total).findFirst().get().get();

		assertThat(result).isEqualTo(6);
	}

	@Test
	public void maxTakesTheLargest() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> m = lvar();

		int result = Aggregate.max(x, x.unifies(1).or(x.unifies(3)).or(x.unifies(2)), m)
				.solve(m).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void minTakesTheSmallest() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> m = lvar();

		int result = Aggregate.min(x, x.unifies(3).or(x.unifies(1)).or(x.unifies(2)), m)
				.solve(m).findFirst().get().get();

		assertThat(result).isEqualTo(1);
	}

	@Test
	public void maxOfAFailingGoalFails() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> m = lvar();

		long count = Aggregate.max(x, x.unifies(1).and(x.unifies(2)), m).solve(m).count();

		assertThat(count).isZero();
	}

	@Test
	public void countReflectsAnEnclosingRelation() {
		// alice's descendants, counted inside the logic
		Unifiable<String> d = lvar();
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count(descendant(d), n).solve(n).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void countIsCorrectUnderTheParallelScheduler() {
		// atomic accumulation + exploration completing after the fork joins
		for (int i = 0; i < 20; i++) {
			Unifiable<Integer> x = lvar();
			Unifiable<Integer> n = lvar();

			int result = Aggregate.count(oneTwoThree(x), n)
					.solveParallel(n).findFirst().get().get();

			assertThat(result).isEqualTo(3);
		}
	}

	// bob, charlie, david are alice's descendants
	private Goal descendant(Unifiable<String> d) {
		return d.unifies("bob").or(d.unifies("charlie")).or(d.unifies("david"));
	}
}
