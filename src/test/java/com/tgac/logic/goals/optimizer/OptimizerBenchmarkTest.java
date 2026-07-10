package com.tgac.logic.goals.optimizer;

// ABOUTME: Proves the ordering optimizer reduces search work: identical answers,
// ABOUTME: an order of magnitude fewer branch spawns on a mis-ordered query.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.Test;

public class OptimizerBenchmarkTest {

	private static final int N = 60;

	/** Fires once per branch the search actually spawns; order 1 so it stays sortable. */
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

	/** A generator: N alternatives, each probed — derived order is N. */
	private static Goal oneOf(Unifiable<Integer> x, AtomicLong spawns) {
		Goal acc = new Probe(spawns).and(unify(x, lval(1)));
		for (int i = 2; i <= N; i++) {
			acc = acc.or(new Probe(spawns).and(unify(x, lval(i))));
		}
		return acc;
	}

	/** generate-then-test, deliberately mis-ordered: filters last. */
	private static Goal misOrdered(Unifiable<Integer> x, Unifiable<Integer> y, AtomicLong spawns) {
		return oneOf(x, spawns).and(oneOf(y, spawns))
				.and(unify(x, lval(7))).and(unify(y, lval(3)));
	}

	@Test
	public void orderingCutsBranchSpawnsByAnOrderOfMagnitude() {
		Unifiable<Integer> x1 = lvar(), y1 = lvar();
		AtomicLong plain = new AtomicLong();
		assertThat(misOrdered(x1, y1, plain).solve(x1)
				.map(Object::toString).collect(Collectors.toList()))
				.containsExactly("{7}");

		Unifiable<Integer> x2 = lvar(), y2 = lvar();
		AtomicLong planned = new AtomicLong();
		assertThat(misOrdered(x2, y2, planned)
				.solve(x2, Optimizer.pipeline(new CascadingOptimizer(), new OrderingOptimizer()))
				.map(Object::toString).collect(Collectors.toList()))
				.containsExactly("{7}");

		// generate-then-test spawns ~N + N*N branches; constrain-then-generate ~2N
		assertThat(plain.get()).isGreaterThan((long) N * N);
		assertThat(planned.get() * 10).isLessThan(plain.get());
	}
}
