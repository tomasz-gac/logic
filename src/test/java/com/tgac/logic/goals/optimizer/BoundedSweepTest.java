package com.tgac.logic.goals.optimizer;

// ABOUTME: Pins the Bounded sweep: constraint posts price 1, failure prices 0 and
// ABOUTME: kills segments by sorting, FD constrain-first cuts branch spawns.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.Tuple;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.Test;

public class BoundedSweepTest {

	private static final int N = 40;

	@Value
	private static class Probe implements Goal, Bounded {
		AtomicLong spawns;

		@Override
		public Cont<Package, Nothing> apply(Package s) {
			spawns.incrementAndGet();
			return Cont.just(s);
		}

		@Override
		public long answers(Substitutions s) {
			return 1;
		}
	}

	@Test
	public void constraintPostsPriceAtOne() {
		Unifiable<Long> x = lvar(), y = lvar();
		assertThat(order(FiniteDomain.dom(x, EnumeratedDomain.range(0L, 9L)))).isEqualTo(1);
		assertThat(order(FiniteDomain.leq(x, y))).isEqualTo(1);
		assertThat(order(Disequality.separate(x, y))).isEqualTo(1);
		assertThat(order(Goal.success())).isEqualTo(1);
		assertThat(order(Goal.failure())).isEqualTo(0);
	}

	private static long order(Goal g) {
		return ((Bounded) g).answers(Substitutions.empty());
	}

	private static Goal oneOf(Unifiable<Long> x, AtomicLong spawns) {
		Goal acc = new Probe(spawns).and(unify(x, lval(1L)));
		for (long i = 2; i <= N; i++) {
			acc = acc.or(new Probe(spawns).and(unify(x, lval(i))));
		}
		return acc;
	}

	@Test
	public void constrainFirstCutsFdBranchSpawns() {
		// generate-then-constrain, deliberately mis-ordered: domains posted last
		Unifiable<Long> x1 = lvar(), y1 = lvar();
		AtomicLong plain = new AtomicLong();
		assertThat(misOrdered(x1, y1, plain).solve(x1)
				.map(Object::toString).collect(Collectors.toList()))
				.containsExactly("{7}");

		Unifiable<Long> x2 = lvar(), y2 = lvar();
		AtomicLong planned = new AtomicLong();
		assertThat(misOrdered(x2, y2, planned)
				.solve(x2, Optimizer.pipeline(new CascadingOptimizer(), new OrderingOptimizer()))
				.map(Object::toString).collect(Collectors.toList()))
				.containsExactly("{7}");

		assertThat(plain.get()).isGreaterThan((long) N * N);
		assertThat(planned.get() * 10).isLessThan(plain.get());
	}

	@Test
	public void groundFalseUnificationPricesZeroAndKillsItsSegment() {
		// the dead filter is written LAST; dynamic pricing sorts it first
		Unifiable<Long> x = lvar();
		AtomicLong plain = new AtomicLong();
		assertThat(oneOf(x, plain).and(lval(1L).unifies(lval(2L))).solve(x).count()).isZero();
		assertThat(plain.get()).isEqualTo(N);

		Unifiable<Long> x2 = lvar();
		AtomicLong planned = new AtomicLong();
		assertThat(oneOf(x2, planned).and(lval(1L).unifies(lval(2L)))
				.solve(x2, new OrderingOptimizer()).count()).isZero();
		assertThat(planned.get()).isZero();

		// partially-ground contradiction: heads clash through free tails — 0 too
		Unifiable<Long> xp = lvar();
		AtomicLong partial = new AtomicLong();
		assertThat(oneOf(xp, partial)
				.and(lval(Tuple.of(lvar(), 1L)).unifies(lval(Tuple.of(lvar(), 2L))))
				.solve(xp, new OrderingOptimizer()).count()).isZero();
		assertThat(partial.get()).isZero();

		// and the ground-TRUE twin stays order 1: the segment survives
		Unifiable<Long> x3 = lvar();
		assertThat(oneOf(x3, new AtomicLong()).and(lval(1L).unifies(lval(1L)))
				.solve(x3, new OrderingOptimizer()).count()).isEqualTo(N);
	}

	private static Goal misOrdered(Unifiable<Long> x, Unifiable<Long> y, AtomicLong spawns) {
		return oneOf(x, spawns).and(oneOf(y, spawns))
				.and(FiniteDomain.dom(x, EnumeratedDomain.range(7L, 8L)))
				.and(FiniteDomain.dom(y, EnumeratedDomain.range(3L, 4L)));
	}

	@Test
	public void failureSortsFirstAndKillsTheSegmentBeforeGeneration() {
		Unifiable<Long> x = lvar();
		AtomicLong plain = new AtomicLong();
		assertThat(oneOf(x, plain).and(Goal.failure()).solve(x).count()).isZero();
		assertThat(plain.get()).isEqualTo(N);

		Unifiable<Long> x2 = lvar();
		AtomicLong planned = new AtomicLong();
		assertThat(oneOf(x2, planned).and(Goal.failure())
				.solve(x2, new OrderingOptimizer()).count()).isZero();
		assertThat(planned.get()).isZero();
	}
}
