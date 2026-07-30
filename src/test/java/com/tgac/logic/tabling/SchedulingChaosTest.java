package com.tgac.logic.tabling;

// ABOUTME: Order-independence as a property: the same goal solved under many
// ABOUTME: random scheduling seeds must yield the same solutions - any seed that
// ABOUTME: differs is an order-dependence bug, replayable by its seed.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.RandomizedScheduler;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Test;

public class SchedulingChaosTest {

	private static final int SEEDS = 24;

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	/**
	 * The property: the goal's sorted solutions are identical under the
	 * default driver and under every chaos seed. The goal is re-built per
	 * run — tables are per-solve, and relation identity must not leak
	 * across runs.
	 */
	private static void orderFree(Supplier<Tuple2<Goal, Unifiable<Integer>>> program) {
		Tuple2<Goal, Unifiable<Integer>> reference = program.get();
		List<Integer> expected = reference._1.solve(reference._2)
				.map(Term::<Integer>get).sorted().collect(Collectors.toList());
		for (long seed = 0; seed < SEEDS; seed++) {
			Tuple2<Goal, Unifiable<Integer>> chaotic = program.get();
			long s = seed;
			List<Integer> actual = chaotic._1.solve(chaotic._2, f -> RandomizedScheduler.of(f, s))
					.map(Term::<Integer>get).sorted().collect(Collectors.toList());
			assertThat(actual)
					.as("seed %d must match the default driver", s)
					.containsExactlyElementsOf(expected);
		}
	}

	@Test
	public void entailmentDedupIsOrderFree() {
		// the week's bug, as a property: whichever order the wide and narrow
		// regions derive in, the delivered set is the maximal antichain's
		orderFree(() -> {
			Tabled<Tuple1<Unifiable<Integer>>> gen =
					Tabling.define(args -> args.apply(x ->
							FiniteDomain.dom(x, dom(1, 2))
									.or(FiniteDomain.dom(x, dom(1, 2, 3)))));
			Unifiable<Integer> x = lvar();
			return Tuple.of(gen.apply(Tuple.of(x)), x);
		});
	}

	@Test
	public void nestedTabledConsumptionIsOrderFree() {
		orderFree(() -> {
			Tabled<Tuple1<Unifiable<Integer>>> inner =
					Tabling.define(args -> args.apply(x ->
							FiniteDomain.dom(x, dom(1, 2))
									.or(FiniteDomain.dom(x, dom(1, 2, 3)))));
			Tabled<Tuple1<Unifiable<Integer>>> outer =
					Tabling.define(args -> args.apply(x ->
							inner.apply(Tuple.of(x))));
			Unifiable<Integer> x = lvar();
			return Tuple.of(outer.apply(Tuple.of(x)), x);
		});
	}

	@Test
	public void recursiveTablingIsOrderFree() {
		// left recursion through the table: the classic termination shape,
		// now also pinned order-free
		orderFree(() -> {
			Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> edge =
					Tabling.define(args -> args.apply((x, y) ->
							unify(x, lval(1)).and(unify(y, lval(2)))
									.or(unify(x, lval(2)).and(unify(y, lval(3))))
									.or(unify(x, lval(3)).and(unify(y, lval(4))))));
			Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							edge.apply(Tuple.of(x, y)).or(Goal.defer(() -> {
								Unifiable<Integer> z = lvar();
								return self.apply(Tuple.of(x, z)).and(edge.apply(Tuple.of(z, y)));
							}))));
			Unifiable<Integer> dest = lvar();
			return Tuple.of(path.apply(Tuple.of(lval(1), dest)), dest);
		});
	}
}
