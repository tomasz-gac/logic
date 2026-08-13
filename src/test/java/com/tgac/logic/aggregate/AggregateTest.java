package com.tgac.logic.aggregate;

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.projection.Projection;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class AggregateTest {

	private Goal oneTwoThree(Unifiable<Integer> x) {
		return x.unifies(1).or(x.unifies(2)).or(x.unifies(3));
	}

	@Test
	public void findallCollectsEverySolution() {
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall((Unifiable<Integer> x) -> oneTwoThree(x), result);

		List<Integer> list = g.solve(result, TestSchedulers.factory()).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());
		assertThat(list).containsExactlyInAnyOrder(1, 2, 3);
	}

	@Test
	public void findallSucceedsExactlyOnce() {
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall((Unifiable<Integer> x) -> oneTwoThree(x), result);

		assertThat(g.solve(result, TestSchedulers.factory()).count()).isEqualTo(1);
	}

	@Test
	public void findallOfAFailingGoalIsTheEmptyList() {
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall((Unifiable<Integer> x) -> x.unifies(1).and(x.unifies(2)), result);

		List<Integer> list = g.solve(result, TestSchedulers.factory()).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());
		assertThat(list).isEmpty();
	}

	@Test
	public void findallLeavesTheOuterWorldUntouched() {
		// the collected copies are independent; an outer variable stays free
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall((Unifiable<Integer> t) -> oneTwoThree(t), result)
				.and(x.unifies(99));

		Integer bound = g.solve(x, TestSchedulers.factory()).findFirst().get().get();
		assertThat(bound).isEqualTo(99);
	}

	@Test
	public void countCountsSolutions() {
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count((Unifiable<Integer> x) -> oneTwoThree(x), n)
				.solve(n, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void countCountsDistinctSolutionsNotDeliveries() {
		// x = 2 is proven twice; the answer set is {1, 2}
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count((Unifiable<Integer> x) ->
						x.unifies(1).or(x.unifies(2)).or(x.unifies(2)), n)
				.solve(n, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(2);
	}

	@Test
	public void countAgreesWithTheBoundaryFold() {
		// the boundary idiom is the oracle: solve, distinct, count
		Unifiable<Integer> x = lvar();
		Goal dup = x.unifies(1).or(x.unifies(2)).or(x.unifies(2));
		long boundary = dup.solve(x, TestSchedulers.factory()).distinct().count();

		Unifiable<Integer> n = lvar();
		int inGoal = Aggregate.count((Unifiable<Integer> t) ->
						t.unifies(1).or(t.unifies(2)).or(t.unifies(2)), n)
				.solve(n, TestSchedulers.factory()).findFirst().get().get();

		assertThat((long) inGoal).isEqualTo(boundary);
	}

	@Test
	public void countRefusesANonGroundSolution() {
		// x with x != 3 denotes infinitely many distinct tuples — and counting
		// answer RECORDS would be representation-sensitive: {1,2} as one region
		// or as two records denotes the same set with different record counts
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) -> exclude(x.unifies(lval(3))), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("free");
	}

	@Test
	public void countRefusesAWhollyFreeSolution() {
		// no residual needed: a free template alone denotes every value
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) -> Goal.success(), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("free");
	}

	@Test
	public void aGroundSolutionWithAResidualWitnessCounts() {
		// the solution tuple is ground; the constrained variable is a body
		// local, existentially quantified — one distinct solution
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) ->
				Goal.defer(() -> {
					Unifiable<Integer> y = lvar();
					return x.unifies(1).and(exclude(y.unifies(lval(3))));
				}), n);

		assertThat(g.solve(n, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(1);
	}

	@Test
	public void countOfAFailingGoalIsZero() {
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count((Unifiable<Integer> x) -> x.unifies(1).and(x.unifies(2)), n)
				.solve(n, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isZero();
	}

	@Test
	public void sumAddsTheExpression() {
		Unifiable<Integer> total = lvar();

		int result = Aggregate.sum(x -> oneTwoThree(x), total)
				.solve(total, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(6);
	}

	@Test
	public void sumIsOverDistinctSolutions() {
		// x = 2 proven twice contributes once: {1, 2} sums to 3
		Unifiable<Integer> total = lvar();

		int result = Aggregate.sum((Unifiable<Integer> x) ->
						x.unifies(1).or(x.unifies(2)).or(x.unifies(2)), total)
				.solve(total, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void sumFoldsOncePerDistinctSolutionNotPerDistinctPayload() {
		// product(a, 10) and product(b, 10): two solutions, equal payloads —
		// the payload is not the solution identity
		Unifiable<Integer> total = lvar();

		int result = Aggregate.sum((Unifiable<String> s, Unifiable<Integer> v) ->
						s.unifies("a").and(v.unifies(10))
								.or(s.unifies("a").and(v.unifies(10)))
								.or(s.unifies("b").and(v.unifies(10))), total)
				.solve(total, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(20);
	}

	@Test
	public void maxTakesTheLargest() {
		Unifiable<Integer> m = lvar();

		int result = Aggregate.max(x -> x.unifies(1).or(x.unifies(3)).or(x.unifies(2)), m)
				.solve(m, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void minTakesTheSmallest() {
		Unifiable<Integer> m = lvar();

		int result = Aggregate.min(x -> x.unifies(3).or(x.unifies(1)).or(x.unifies(2)), m)
				.solve(m, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(1);
	}

	@Test
	public void maxOfAFailingGoalFails() {
		Unifiable<Integer> m = lvar();

		long count = Aggregate.max(x -> x.unifies(1).and(x.unifies(2)), m)
				.solve(m, TestSchedulers.factory()).count();

		assertThat(count).isZero();
	}

	@Test
	public void countReflectsAnEnclosingRelation() {
		// alice's descendants, counted inside the logic
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count((Unifiable<String> d) -> descendant(d), n)
				.solve(n, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(3);
	}

	@Test
	public void countIsCorrectUnderTheParallelScheduler() {
		// atomic accumulation + exploration completing after the fork joins
		for (int i = 0; i < 20; i++) {
			Unifiable<Integer> n = lvar();

			int result = Aggregate.count((Unifiable<Integer> x) -> oneTwoThree(x), n)
					.solveParallel(n).findFirst().get().get();

			assertThat(result).isEqualTo(3);
		}
	}

	@Test
	public void findallEnumeratesAFiniteDomain() {
		Unifiable<LList<Long>> result = lvar();

		Goal g = Aggregate.findall((Unifiable<Long> i) -> dom(i, EnumeratedDomain.range(0L, 6L)), result);

		List<Long> list = g.solve(result, TestSchedulers.factory()).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());
		assertThat(list).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L);
	}

	@Test
	public void countCountsFiniteDomainSolutions() {
		Unifiable<Integer> n = lvar();

		int result = Aggregate.count((Unifiable<Long> i) -> dom(i, EnumeratedDomain.range(0L, 6L)), n)
				.solve(n, TestSchedulers.factory()).findFirst().get().get();

		assertThat(result).isEqualTo(6);
	}

	@Test
	public void findallRespectsDisequalityWhenAnswersAreGround() {
		Unifiable<LList<Integer>> result = lvar();

		Goal g = Aggregate.findall((Unifiable<Integer> x) ->
						x.unifies(2).or(x.unifies(3)).or(x.unifies(4)).and(exclude(x.unifies(lval(3)))),
				result);

		List<Integer> list = g.solve(result, TestSchedulers.factory()).findFirst().get().get()
				.toValueStream().collect(Collectors.toList());
		assertThat(list).containsExactlyInAnyOrder(2, 4);
	}

	// bob, charlie, david are alice's descendants
	private Goal descendant(Unifiable<String> d) {
		return d.unifies("bob").or(d.unifies("charlie")).or(d.unifies("david"));
	}

	@Test
	public void countRefusesABodyTouchingAPreExistingVariable() {
		Unifiable<Integer> y = lvar("smuggled");
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) -> x.unifies(y), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("smuggled");
	}

	@Test
	public void theRefusalNamesEveryPreExistingVariableItFound() {
		Unifiable<Integer> y = lvar("first");
		Unifiable<Integer> z = lvar("second");
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<LList<Integer>> x) ->
				x.unifies(LList.ofAll(y, z)), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("first")
				.hasMessageContaining("second");
	}

	@Test
	public void countRefusesABodyConstrainingAPreExistingVariable() {
		// a constraint stated on an outer variable binds nothing — it enters
		// through the statement seam, not the binding seam — but the answer
		// set depends on it all the same
		Unifiable<Integer> y = lvar("constrained");
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) ->
				exclude(y.unifies(lval(1))).and(x.unifies(1)), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("constrained");
	}

	@Test
	public void countRefusesABodySuspendedOnAPreExistingVariable() {
		// a projection parked on an outer variable can never ripen inside a
		// closed sub-solve; silently failing the branch would make the count
		// conditional on outside state
		Unifiable<Integer> y = lvar("watched");
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) ->
				Projection.project(y, v -> x.unifies(v)), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("watched");
	}

	@Test
	public void countRefusesAnInlineRipeSuspensionDeclaringAPreExistingVariable() {
		// ripeness is any upward-closed condition — it can pass without the
		// watched variable being bound, running the body inline; the watched
		// set is the body's DECLARED read surface, so it is checked before
		// ripeness, not only on the parking branch
		Unifiable<Integer> y = lvar("declared");
		Unifiable<Integer> n = lvar();

		Goal g = Aggregate.count((Unifiable<Integer> x) ->
				Propagation.suspend(Collections.singletonList(y), s -> true, x.unifies(1)), n);

		assertThatThrownBy(() -> g.solve(n, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("declared");
	}

	@Test
	public void aPreExistingVariableAlreadyBoundToGroundIsAdmitted() {
		// the walk dissolves a bound outer variable into its value before
		// the sub-solve ever sees a variable — the free surface is empty
		Unifiable<Integer> y = lvar("bound");
		Unifiable<Integer> n = lvar();

		Goal g = y.unifies(5)
				.and(Aggregate.count((Unifiable<Integer> x) -> x.unifies(y), n));

		assertThat(g.solve(n, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(1);
	}
}
