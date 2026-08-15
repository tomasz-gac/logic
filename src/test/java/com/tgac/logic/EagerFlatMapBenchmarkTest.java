package com.tgac.logic;

// ABOUTME: Time-based measurement of the eager Done.flatMap optimization: the
// ABOUTME: vision-lane workloads run with the budget on (512) and off (0).

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.interpreter.EngineGuard;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.collection.Array;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;

/**
 * A measuring instrument, not a pin: each vision-lane workload runs with the
 * eager budget ON (the suite's 512) and OFF (0, every apply becomes a heap
 * node), interleaved after shared warmup, best-of per configuration. The one
 * assertion is the budgeted-eagerness law's receipt — the answer COUNT is
 * budget-invariant — because eagerness may only ever change representation
 * and cost, never answers. Timings land in
 * {@code target/eager-flatmap-benchmark.txt} for the reader; wall times are
 * machine facts, so nothing here pins them.
 */
public class EagerFlatMapBenchmarkTest {

	private static final int WARMUP_ROUNDS = 2;
	private static final int MEASURED_ROUNDS = 3;
	private static final int BUDGET_ON = 16;
	private static final int BUDGET_OFF = 0;

	@Test
	public void measuresEagerFlatMapOnAndOff() throws IOException {
		List<String> report = new ArrayList<>();
		report.add(String.format("%-22s %10s %10s %8s", "workload", "on(ms)", "off(ms)", "off/on"));
		lane(report, "appendo-splits", 100, EagerFlatMapBenchmarkTest::appendoSplits);
		lane(report, "distinct-pairs", 60, EagerFlatMapBenchmarkTest::distinctPairs);
		lane(report, "fd-ring", 150, EagerFlatMapBenchmarkTest::fdRing);
		lane(report, "tabled-ring-closure", 60, EagerFlatMapBenchmarkTest::tabledRingClosure);
		Files.write(Paths.get("target/eager-flatmap-benchmark.txt"), report);
	}

	private static void lane(List<String> report, String name, int reps, Supplier<Long> workload) {
		long onAnswers = withBudget(BUDGET_ON, workload);
		long offAnswers = withBudget(BUDGET_OFF, workload);
		assertThat(offAnswers).as(name + " answers are budget-invariant").isEqualTo(onAnswers);

		for (int w = 0; w < WARMUP_ROUNDS; w++) {
			withBudget(BUDGET_OFF, () -> repeat(reps, workload));
			withBudget(BUDGET_ON, () -> repeat(reps, workload));
		}
		long onBest = Long.MAX_VALUE;
		long offBest = Long.MAX_VALUE;
		for (int r = 0; r < MEASURED_ROUNDS; r++) {
			offBest = Math.min(offBest, withBudget(BUDGET_OFF, () -> timed(reps, workload)));
			onBest = Math.min(onBest, withBudget(BUDGET_ON, () -> timed(reps, workload)));
		}
		report.add(String.format("%-22s %10.2f %10.2f %8.2f",
				name, onBest / 1e6, offBest / 1e6, (double) offBest / onBest));
	}

	private static long repeat(int reps, Supplier<Long> workload) {
		long total = 0;
		for (int i = 0; i < reps; i++) {
			total += workload.get();
		}
		return total;
	}

	private static long timed(int reps, Supplier<Long> workload) {
		long t0 = System.nanoTime();
		repeat(reps, workload);
		return System.nanoTime() - t0;
	}

	private static <T> T withBudget(int budget, Supplier<T> body) {
		int old = EngineGuard.eagerBudget();
		EngineGuard.setEagerBudget(budget);
		try {
			return body.get();
		} finally {
			EngineGuard.setEagerBudget(old);
		}
	}

	/** Pure relational lane: every split of a six-element list. */
	private static long appendoSplits() {
		Unifiable<LList<Integer>> front = lvar();
		Unifiable<LList<Integer>> back = lvar();
		Unifiable<LList<Integer>> both = lvar();
		return unify(both, LList.ofAll(1, 2, 3, 4, 5, 6))
				.and(Logic.appendo(front, back, both))
				.solve(both)
				.count();
	}

	/** Disequality lane: ordered distinct pairs from a five-element menu. */
	private static long distinctPairs() {
		Unifiable<LList<Integer>> menu = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		return unify(menu, LList.ofAll(1, 2, 3, 4, 5))
				.and(exclude(a.unifies(b)))
				.and(Logic.membero(a, menu))
				.and(Logic.membero(b, menu))
				.solve(a)
				.count();
	}

	/** FD lane: propagation and labelling through a ring step. */
	private static long fdRing() {
		Domain<Integer> rooms = dom(1, 2, 3, 4, 5);
		Unifiable<Integer> from = lvar();
		Unifiable<Integer> to = lvar();
		return FiniteDomain.dom(from, rooms)
				.and(FiniteDomain.dom(to, rooms))
				.and(FiniteDomain.addo(from, lval(1), to)
						.or(unify(from, lval(5)).and(unify(to, lval(1)))))
				.solve(from)
				.count();
	}

	/** Tabling + TCLP lane: the ring closure — recursion under live domains. */
	private static long tabledRingClosure() {
		Domain<Integer> rooms = dom(1, 2, 3, 4, 5);
		Tabled<Tuple1<Unifiable<Integer>>> reachable =
				Tabling.defineRecursive(self -> args -> args.apply(room ->
						unify(room, lval(5))
								.or(Logic.<Integer> exist(prev ->
										FiniteDomain.dom(prev, rooms)
												.and(FiniteDomain.dom(room, rooms))
												.and(Goal.defer(() -> self.apply(Tuple.of(prev))))
												.and(FiniteDomain.addo(prev, lval(1), room)
														.or(unify(prev, lval(5)).and(unify(room, lval(1)))))))));
		Unifiable<Integer> room = lvar();
		return reachable.apply(Tuple.of(room))
				.solve(room)
				.count();
	}

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}
}
