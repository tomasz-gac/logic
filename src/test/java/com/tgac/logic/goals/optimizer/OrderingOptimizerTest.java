package com.tgac.logic.goals.optimizer;

// ABOUTME: Pins the ambient ordering layer: ascending sort within barrier-delimited
// ABOUTME: segments, derived orders through combinators, and ambient-solve equivalence.

import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.tabling.Tabling;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.Test;

public class OrderingOptimizerTest {

	@Value
	private static class FixedOrder implements Goal, Bounded {
		long n;

		@Override
		public long answers(Substitutions s) {
			return n;
		}

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			return Cont.just(s);
		}
	}

	private static Goal opaque() {
		return s -> Cont.just(s);
	}

	/** Prices 5 blind, 1 sighted — pins that the pass prices with the package. */
	@Value
	private static class StoreSighted implements Goal, Bounded {
		@Override
		public long answers(Substitutions s) {
			return 5;
		}

		@Override
		public long answers(Package p) {
			return 1;
		}

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			return Cont.just(s);
		}
	}

	@Test
	public void pricesWithThePackageNotJustTheSubstitution() {
		Goal sighted = new StoreSighted();
		Goal b3 = new FixedOrder(3);
		Goal sorted = b3.and(sighted).accept(new OrderingOptimizer()).get();
		assertThat(((Conjunction) sorted).getClauses())
				.containsExactly(sighted, b3);
	}

	@Test
	public void sortsSegmentsAscendingAroundBarriers() {
		Goal b5 = new FixedOrder(5), b1 = new FixedOrder(1), b3 = new FixedOrder(3), b2 = new FixedOrder(2);
		Goal barrier = opaque();
		Goal sorted = b5.and(b1).and(barrier).and(b3).and(b2)
				.accept(new OrderingOptimizer()).get();
		assertThat(((Conjunction) sorted).getClauses())
				.containsExactly(b1, b5, barrier, b2, b3);
	}

	@Test
	public void derivesOrderThroughDisjunction() {
		Goal b2 = new FixedOrder(2), b3 = new FixedOrder(3), b4 = new FixedOrder(4);
		// conde(2, 3) has derived order 5 — the order-4 leaf sorts ahead of it
		Goal conde = b2.or(b3);
		Goal sorted = conde.and(b4).accept(new OrderingOptimizer()).get();
		assertThat(((Conjunction) sorted).getClauses())
				.containsExactly(b4, conde);
	}

	@Test
	public void saturationClampsAndZeroAnnihilates() {
		assertThat(Semirings.SATURATING.times(Long.MAX_VALUE / 2, 3L)).isEqualTo(Long.MAX_VALUE);
		assertThat(Semirings.SATURATING.plus(Long.MAX_VALUE, 1L)).isEqualTo(Long.MAX_VALUE);
		assertThat(Semirings.SATURATING.times(0L, Long.MAX_VALUE)).isEqualTo(0);
	}

	@Test
	public void tabledCallsAreExplicitBarriers() {
		Goal tabled = Tabling.<Tuple1<Unifiable<Integer>>> define(t -> Goal.success())
				.apply(Tuple.of(lvar()));
		assertThat(tabled).isInstanceOf(Barrier.class);

		Goal b5 = new FixedOrder(5), b1 = new FixedOrder(1), b3 = new FixedOrder(3), b2 = new FixedOrder(2);
		Goal sorted = b5.and(b1).and(tabled).and(b3).and(b2)
				.accept(new OrderingOptimizer()).get();
		assertThat(((Conjunction) sorted).getClauses())
				.containsExactly(b1, b5, tabled, b2, b3);
	}

	@Test
	public void ambientSolveYieldsTheSameAnswers() {
		Unifiable<Integer> x = lvar();
		Goal g = unify(x, lval(3)).or(unify(x, lval(4)));
		assertThat(g.solve(x, Optimizer.pipeline(new CascadingOptimizer(), new OrderingOptimizer()))
				.map(Object::toString).collect(Collectors.toList()))
				.hasSameElementsAs(
						g.solve(x).map(Object::toString).collect(Collectors.toList()));
	}

	@Test
	public void recursionUnfoldsThroughTheDeferHook() {
		Unifiable<Integer> x = lvar();
		assertThat(countdown(x, 3).solve(x, new OrderingOptimizer())
				.map(Object::toString).collect(Collectors.toList()))
				.hasSameElementsAs(
						countdown(x, 3).solve(x).map(Object::toString).collect(Collectors.toList()));
	}

	private static Goal countdown(Unifiable<Integer> x, int n) {
		return n == 0 ?
				unify(x, lval(0)) :
				unify(x, lval(n)).or(Goal.defer(() -> countdown(x, n - 1)));
	}
}
