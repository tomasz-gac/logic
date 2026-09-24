package org.clauseway.logic.goals.optimizer;

// ABOUTME: Pins the Bounded sweep: constraint posts price 1, failure prices 0 and
// ABOUTME: kills segments by sorting, FD constrain-first cuts branch spawns.

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.category.Nothing;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.Substitutions;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Tuple;
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
		assertThat(order(FiniteDomain.dom(x, Longs.range(0, 9)))).isEqualTo(1);
		assertThat(order(Longs.leq(x, y))).isEqualTo(1);
		assertThat(order(exclude(x.unifies(y)))).isEqualTo(1);
		// even a ground-false unification: refutation is doom's, not the count's
		assertThat(order(lval(1L).unifies(lval(2L)))).isEqualTo(1);
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
		assertThat(misOrdered(x1, y1, plain).solve(x1, TestSchedulers.factory())
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
	public void groundFalseUnificationSortsFirstAndFailsBeforeGeneration() {
		// the dead filter is written LAST; constrain-first sorts it (order 1)
		// ahead of the generator, where the clash fails at apply — the
		// rewrite-time kill is DoomPruner's, receipted in DoomPrunerTest
		Unifiable<Long> x = lvar();
		AtomicLong plain = new AtomicLong();
		assertThat(oneOf(x, plain).and(lval(1L).unifies(lval(2L))).solve(x, TestSchedulers.factory()).count()).isZero();
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

	@Test
	public void deadPostsSortFirstAndFailBeforeGeneration() {
		// doom never prices: each dead post ranks 1 like any post, which
		// constrain-first sorts ahead of the generator — the kill here is
		// the post failing at apply, not a zero in the sort key (the
		// rewrite-time kill is DoomPruner's, receipted in DoomPrunerTest)
		Goal[] dead = {
				FiniteDomain.dom(lval(5L), Longs.range(0, 3)),
				Longs.leq(lval(5L), lval(2L)),
				Longs.separate(lval(1L), lval(1L)),
				exclude(lval(1L).unifies(lval(1L)))};
		for (Goal deadPost : dead) {
			Unifiable<Long> x = lvar();
			AtomicLong planned = new AtomicLong();
			assertThat(oneOf(x, planned).and(deadPost)
					.solve(x, new OrderingOptimizer()).count()).isZero();
			assertThat(planned.get()).describedAs(deadPost.toString()).isZero();
		}
	}

	private static Goal misOrdered(Unifiable<Long> x, Unifiable<Long> y, AtomicLong spawns) {
		return oneOf(x, spawns).and(oneOf(y, spawns))
				.and(FiniteDomain.dom(x, Longs.range(7, 8)))
				.and(FiniteDomain.dom(y, Longs.range(3, 4)));
	}

	@Test
	public void failureSortsFirstAndKillsTheSegmentBeforeGeneration() {
		Unifiable<Long> x = lvar();
		AtomicLong plain = new AtomicLong();
		assertThat(oneOf(x, plain).and(Goal.failure()).solve(x, TestSchedulers.factory()).count()).isZero();
		assertThat(plain.get()).isEqualTo(N);

		Unifiable<Long> x2 = lvar();
		AtomicLong planned = new AtomicLong();
		assertThat(oneOf(x2, planned).and(Goal.failure())
				.solve(x2, new OrderingOptimizer()).count()).isZero();
		assertThat(planned.get()).isZero();
	}
}
