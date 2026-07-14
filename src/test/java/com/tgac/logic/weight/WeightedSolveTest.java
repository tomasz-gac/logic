package com.tgac.logic.weight;

// ABOUTME: Weighted inference end to end: factor injects per-choice weights,
// ABOUTME: solve ⊕-folds them, and one pass computes count and probability together.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.projection.ProjectionConstraints;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class WeightedSolveTest {

	/** Probability (+, ×) over [0,1]; kept test-local — main must not invite
	 *  correlated-proof misuse (see semiring-inference.md §6). */
	private static final Semiring<Double> PROB = new Semiring<Double>() {
		@Override
		public Double zero() {
			return 0.0;
		}

		@Override
		public Double one() {
			return 1.0;
		}

		@Override
		public Double plus(Double a, Double b) {
			return a + b;
		}

		@Override
		public Double times(Double a, Double b) {
			return a * b;
		}
	};

	private static Goal die(Unifiable<Integer> x) {
		return Logic.membero(x, LList.ofAll(1, 2, 3, 4, 5, 6))
				.and(Weights.factor(PROB, 1.0 / 6));
	}

	@Test
	public void twoDiceSumSevenCountsAndWeighsInOnePass() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Semiring<SemiringStore> product = SemiringStore.product(Semirings.COUNTING, PROB);

		Goal query = die(a).and(die(b))
				.and(ProjectionConstraints.project(a, b, (av, bv) -> Goal.successIf(av + bv == 7)));

		SemiringStore total = Weights.solve(query, product, BreadthFirstScheduler::new);

		// six mutually exclusive rolls sum to 7
		assertThat(total.get(Semirings.COUNTING)).isEqualTo(6L);
		// each roll has probability (1/6)(1/6); disjoint, so they sum exactly
		assertThat(total.get(PROB)).isCloseTo(6.0 / 36, within(1e-9));
	}

	@Test
	public void shortestRouteAndRouteCountInOnePass() {
		// two routes A→D: via B (1+5=6) and via C (2+2=4); min-plus is idempotent,
		// so this exercises an idempotent semiring alongside a non-idempotent one
		Unifiable<String> mid = lvar();
		Semiring<SemiringStore> product = SemiringStore.product(Semirings.COUNTING, Semirings.MIN_PLUS);

		Goal viaB = unify(mid, lval("B"))
				.and(Weights.factor(Semirings.MIN_PLUS, 1L)).and(Weights.factor(Semirings.MIN_PLUS, 5L));
		Goal viaC = unify(mid, lval("C"))
				.and(Weights.factor(Semirings.MIN_PLUS, 2L)).and(Weights.factor(Semirings.MIN_PLUS, 2L));

		SemiringStore total = Weights.solve(viaB.or(viaC), product, BreadthFirstScheduler::new);

		assertThat(total.get(Semirings.COUNTING)).isEqualTo(2L);   // two routes
		assertThat(total.get(Semirings.MIN_PLUS)).isEqualTo(4L);   // the cheaper one
	}

	@Test
	public void perAnswerWeightsRevealWhichBranchIsCheapest() {
		Unifiable<String> mid = lvar();
		Semiring<SemiringStore> product = SemiringStore.product(Semirings.MIN_PLUS);

		Goal viaB = unify(mid, lval("B"))
				.and(Weights.factor(Semirings.MIN_PLUS, 1L)).and(Weights.factor(Semirings.MIN_PLUS, 5L));
		Goal viaC = unify(mid, lval("C"))
				.and(Weights.factor(Semirings.MIN_PLUS, 2L)).and(Weights.factor(Semirings.MIN_PLUS, 2L));

		List<Tuple2<Reified<String>, SemiringStore>> answers =
				Weights.solveEach(viaB.or(viaC), mid, product, BreadthFirstScheduler::new)
						.collect(Collectors.toList());

		assertThat(answers).hasSize(2);
		assertThat(answers).extracting(p -> p._2.get(Semirings.MIN_PLUS))
				.containsExactlyInAnyOrder(6L, 4L);

		Tuple2<Reified<String>, SemiringStore> cheapest = answers.stream()
				.min(Comparator.comparingLong(p -> p._2.get(Semirings.MIN_PLUS)))
				.get();
		assertThat(cheapest._2.get(Semirings.MIN_PLUS)).isEqualTo(4L);
		assertThat(cheapest._1.toString()).contains("C");
	}

	@Test
	public void noSolutionsFoldsToZero() {
		Unifiable<Integer> a = lvar();
		Semiring<SemiringStore> product = SemiringStore.product(Semirings.COUNTING, PROB);

		Goal query = die(a).and(ProjectionConstraints.project(a, av -> Goal.successIf(av > 100)));

		SemiringStore total = Weights.solve(query, product, BreadthFirstScheduler::new);

		assertThat(total.get(Semirings.COUNTING)).isEqualTo(0L);
		assertThat(total.get(PROB)).isCloseTo(0.0, within(1e-9));
	}
}
