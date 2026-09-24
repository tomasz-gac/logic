package org.clauseway.logic.tabling;

// ABOUTME: Order-independence as a property: the same goal solved under many
// ABOUTME: random scheduling seeds must yield the same solutions - any seed that
// ABOUTME: differs is an order-dependence bug, replayable by its seed.

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.algebra.Semirings;
import org.clauseway.functional.fibers.schedulers.RandomizedScheduler;
import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.weight.SemiringStore;
import org.clauseway.logic.weight.Weights;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple1;
import org.clauseway.functional.tuples.Tuple2;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Test;

public class SchedulingChaosTest {

	private static final int SEEDS = 24;

	private static Domain<Integer> dom(int... values) {
		return Ints.enumerated(Arrays.stream(values).boxed().toArray(Integer[]::new));
	}

	/**
	 * The property: the goal's sorted solutions are identical under the
	 * default driver and under every chaos seed. The goal is re-built per
	 * run — tables are per-solve, and relation identity must not leak
	 * across runs.
	 */
	private static void orderFree(Supplier<Tuple2<Goal, Unifiable<Integer>>> program) {
		Tuple2<Goal, Unifiable<Integer>> reference = program.get();
		List<Integer> expected = reference._1.solve(reference._2, TestSchedulers.factory())
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

	/** The WeightedTablingTest graph: a→b(1), b→d(5), a→c(2), c→d(2). */
	private static Goal edge(Unifiable<String> x, Unifiable<String> y) {
		return unify(x, lval("a")).and(unify(y, lval("b"))).and(Weights.factor(Semirings.MIN_PLUS, 1L))
				.or(unify(x, lval("b")).and(unify(y, lval("d"))).and(Weights.factor(Semirings.MIN_PLUS, 5L)))
				.or(unify(x, lval("a")).and(unify(y, lval("c"))).and(Weights.factor(Semirings.MIN_PLUS, 2L)))
				.or(unify(x, lval("c")).and(unify(y, lval("d"))).and(Weights.factor(Semirings.MIN_PLUS, 2L)));
	}

	@Test
	public void minPlusFoldsAreOrderFree() {
		// the historical order-luck bug: a cheaper cost derived AFTER a
		// consumer passed the key was silently lost. Re-delivery of improved
		// folds makes the shortest path 4 under every schedule
		for (long seed = 0; seed < SEEDS; seed++) {
			long s = seed;
			Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> path =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							edge(x, y).or(Goal.defer(() -> {
								Unifiable<String> z = lvar();
								return edge(x, z).and(self.apply(Tuple.of(z, y)));
							}))));
			Unifiable<String> dest = lvar();
			long shortest = Weights.solveBounded(
							path.apply(Tuple.of(lval("a"), dest)), dest,
							SemiringStore.boundedProduct(Semirings.MIN_PLUS),
							f -> RandomizedScheduler.of(f, s))
					.filter(answer -> answer._1.toString().contains("d"))
					.map(answer -> answer._2.get(Semirings.MIN_PLUS))
					.min(Long::compareTo)
					.orElseThrow(() -> new AssertionError("no path to d under seed " + s));
			assertThat(shortest).as("seed %d", s).isEqualTo(4L);
		}
	}

	@Test
	public void minPlusDeliversFinalValuesExactlyOnce() {
		// an outside reader receives only FINAL facts: a weighted fold is
		// final at the seal, so each term delivers exactly once, carrying
		// the completed value - never a provisional cost, never a refinement
		for (long seed = 0; seed < SEEDS; seed++) {
			long s = seed;
			Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> path =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							edge(x, y).or(Goal.defer(() -> {
								Unifiable<String> z = lvar();
								return edge(x, z).and(self.apply(Tuple.of(z, y)));
							}))));
			Unifiable<String> dest = lvar();
			List<String> terms = Weights.solveBounded(
							path.apply(Tuple.of(lval("a"), dest)), dest,
							SemiringStore.boundedProduct(Semirings.MIN_PLUS),
							f -> RandomizedScheduler.of(f, s))
					.map(answer -> answer._1.toString())
					.collect(Collectors.toList());
			assertThat(terms)
					.as("seed %d delivers each term exactly once", s)
					.doesNotHaveDuplicates();
		}
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
