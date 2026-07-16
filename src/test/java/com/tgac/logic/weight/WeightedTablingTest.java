package com.tgac.logic.weight;

// ABOUTME: Semiring tabling end to end: a recursive tabled path relation under
// ABOUTME: min-plus computes shortest costs — the answer cell folds by ⊕(min).

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class WeightedTablingTest {

	// a weighted graph: a→b(1), b→d(5), a→c(2), c→d(2)
	private static Goal edge(Unifiable<String> x, Unifiable<String> y) {
		return unify(x, lval("a")).and(unify(y, lval("b"))).and(Weights.factor(Semirings.MIN_PLUS, 1L))
				.or(unify(x, lval("b")).and(unify(y, lval("d"))).and(Weights.factor(Semirings.MIN_PLUS, 5L)))
				.or(unify(x, lval("a")).and(unify(y, lval("c"))).and(Weights.factor(Semirings.MIN_PLUS, 2L)))
				.or(unify(x, lval("c")).and(unify(y, lval("d"))).and(Weights.factor(Semirings.MIN_PLUS, 2L)));
	}

	@Test
	public void shortestPathByRecursiveTabling() {
		Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> path =
				Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
						edge(x, y).or(Goal.defer(() -> {
							Unifiable<String> z = lvar();
							return edge(x, z).and(self.apply(Tuple.of(z, y)));
						}))));

		Unifiable<String> dest = lvar();
		BoundedSemiring<SemiringStore> product = SemiringStore.boundedProduct(Semirings.MIN_PLUS);

		// solveBounded names the streaming-tabling strategy at the call site
		List<Tuple2<Reified<String>, SemiringStore>> answers =
				Weights.solveBounded(path.apply(Tuple.of(lval("a"), dest)), dest, product,
								BreadthFirstScheduler::new)
						.collect(Collectors.toList());

		// a→d: min(a→b→d = 6, a→c→d = 4) = 4
		Long shortestToD = answers.stream()
				.filter(p -> p._1.toString().contains("d"))
				.map(p -> p._2.get(Semirings.MIN_PLUS))
				.min(Long::compareTo)
				.orElseThrow(() -> new AssertionError("no path to d"));
		assertThat(shortestToD).isEqualTo(4L);
	}

	@Test
	public void plainWeightedSolveRefusesTabledGoalsInsteadOfDroppingWeights() {
		// a tabled relation under solveEach (non-idempotent counting): the plain
		// table cannot thread weights through the tabling boundary, so instead of
		// silently returning wrong weights it must fail loudly
		Tabled<Unifiable<String>> rel = Tabling.define(x ->
				unify(x, lval("a")).or(unify(x, lval("b"))));
		Unifiable<String> out = lvar();
		Semiring<SemiringStore> counting = SemiringStore.product(Semirings.COUNTING);

		assertThatThrownBy(() ->
				Weights.solveEach(rel.apply(out), out, counting, BreadthFirstScheduler::new)
						.collect(Collectors.toList()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("solveBounded");
	}
}
