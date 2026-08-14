package com.tgac.logic.disjunction;

// ABOUTME: The ratified benchmark (SO 70288953): pairwise non-overlap scheduling
// ABOUTME: as one disjunct per pair, raced against the conde spelling of 2021.

import static com.tgac.logic.disjunction.Disjunction.any;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.interpreter.StepListener;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.functional.fibers.schedulers.ForkJoinScheduler;
import com.tgac.logic.TestSchedulers;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Conde;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The 2021 core.logic question (Stack Overflow 70288953), ported: time strips
 * (start, duration, end, space), {@code stripo} via addo, and pairwise
 * non-overlap — the THREE-LITERAL CLAUSE {@code (sp₁ ≠ sp₂) ∨ (e₁ ≤ s₂) ∨
 * (e₂ ≤ s₁)} — spelled both ways: one disjunct per pair (residence) against
 * conde forking (2021's only option, the observed 2^pairs blowup). Answers
 * compare up to subsumption (all ground after labelling, so directly);
 * the step counts are the race.
 */
public class SchedulingBenchmarkTest {

	private static final class Strip {
		final Unifiable<Long> start = lvar();
		final Unifiable<Long> end = lvar();
		final long space;

		Strip(long space) {
			this.space = space;
		}
	}

	/** stripo: start ∈ 0..horizon−1, end = start + 1 (unit durations). */
	private static Goal strips(List<Strip> strips, long horizon) {
		Goal g = Goal.success();
		for (Strip s : strips) {
			g = g.and(FiniteDomain.dom(s.start, EnumeratedDomain.range(0L, horizon)))
					.and(FiniteDomain.dom(s.end, EnumeratedDomain.range(1L, horizon + 1)))
					.and(FiniteDomain.addo(s.start, lval(1L), s.end));
		}
		return g;
	}

	private static Goal nonOverlapResident(Strip a, Strip b) {
		return any(exclude(lval(a.space).unifies(lval(b.space))),
				FiniteDomain.leq(a.end, b.start),  
				FiniteDomain.leq(b.end, a.start));
	}

	private static Goal nonOverlapConde(Strip a, Strip b) {
		return Conde.of(java.util.Arrays.asList(
				exclude(lval(a.space).unifies(lval(b.space))),
				FiniteDomain.leq(a.end, b.start),
				FiniteDomain.leq(b.end, a.start)));
	}

	private interface Lane {
		Goal pair(Strip a, Strip b);
	}

	private static Goal schedule(List<Strip> ss, long horizon, Lane lane) {
		Goal g = strips(ss, horizon);
		for (int i = 0; i < ss.size(); i++) {
			for (int j = i + 1; j < ss.size(); j++) {
				g = g.and(lane.pair(ss.get(i), ss.get(j)));
			}
		}
		return g;
	}

	private static List<Strip> sameSpace(int n) {
		List<Strip> ss = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ss.add(new Strip(0L));
		}
		return ss;
	}

	private static Unifiable<LList<Long>> starts(List<Strip> ss) {
		return LList.ofAll(ss.size(), i -> ss.get(i).start);
	}

	/**
	 * The 2021 model's OTHER half, happens-beforo: each process is a chain
	 * of operations, operation j in space j; the chain's leqs propagate
	 * windows eagerly — the external knowledge that decides disjuncts.
	 */
	private static List<Strip> jobShop(int processes, int spaces, List<Goal> chains) {
		List<Strip> ops = new ArrayList<>();
		for (int p = 0; p < processes; p++) {
			Strip prev = null;
			for (int j = 0; j < spaces; j++) {
				Strip op = new Strip(j);
				ops.add(op);
				if (prev != null) {
					chains.add(FiniteDomain.leq(prev.end, op.start));
				}
				prev = op;
			}
		}
		return ops;
	}


	private static <T> long steps(Goal goal, Unifiable<T> out) {
		AtomicLong count = new AtomicLong();
		StepListener counting = new StepListener() {
			@Override
			public void onStep(Fiber<?> node) {
				count.incrementAndGet();
			}
		};
		List<Reified<T>> results = goal.solve(out, fiber -> new BreadthFirstScheduler<>(fiber).withListener(counting))
				.collect(Collectors.toList());
		return count.get();
	}

	@Test
	public void answerParityAtThree() {
		// n=3, one space, tight horizon: 3! = 6 schedules, both lanes, same set
		List<Strip> r = sameSpace(3);
		List<String> resident = schedule(r, 3, SchedulingBenchmarkTest::nonOverlapResident)
				.solve(starts(r), TestSchedulers.factory())
				.map(Object::toString).distinct().sorted().collect(Collectors.toList());

		List<Strip> c = sameSpace(3);
		List<String> conde = schedule(c, 3, SchedulingBenchmarkTest::nonOverlapConde)
				.solve(starts(c), TestSchedulers.factory())
				.map(Object::toString).distinct().sorted().collect(Collectors.toList());

		assertThat(resident).hasSize(6).isEqualTo(conde);
	}

	@Test
	public void jobShopParityAtTwoByTwo() {
		// 2 processes through 2 spaces, horizon 4: both lanes, same schedules
		List<Goal> chains1 = new ArrayList<>();
		List<Strip> ops1 = jobShop(2, 2, chains1);
		Goal g1 = schedule(ops1, 4, SchedulingBenchmarkTest::nonOverlapResident);
		for (Goal c : chains1) {
			g1 = g1.and(c);
		}
		List<String> resident = g1.solve(starts(ops1), TestSchedulers.factory())
				.map(Object::toString).distinct().sorted().collect(Collectors.toList());

		List<Goal> chains2 = new ArrayList<>();
		List<Strip> ops2 = jobShop(2, 2, chains2);
		Goal g2 = schedule(ops2, 4, SchedulingBenchmarkTest::nonOverlapConde);
		for (Goal c : chains2) {
			g2 = g2.and(c);
		}
		List<String> conde = g2.solve(starts(ops2), TestSchedulers.factory())
				.map(Object::toString).distinct().sorted().collect(Collectors.toList());

		assertThat(resident).isNotEmpty().isEqualTo(conde);
	}

	@Test
	public void theJobShopRaceAtThreeByThree() {
		// 3 processes through 3 spaces, horizon 6: 36 pairs — 27 cross-space
		// clauses discharge, 9 same-space stay contested under chain pruning.
		// The conde lane is NOT run: measured by hand (Aug 2026), it does
		// not finish in minutes — the 2021 blowup reproduced verbatim —
		// while residence completes in seconds. This pin is the survivor's.
		List<Goal> chains = new ArrayList<>();
		List<Strip> ops = jobShop(3, 3, chains);
		Goal g = schedule(ops, 6, SchedulingBenchmarkTest::nonOverlapResident);
		for (Goal c : chains) {
			g = g.and(c);
		}
		long resident = steps(g, starts(ops));

		assertThat(resident).isEqualTo(274_078L);
	}

	@Test
	public void theRaceAtFive() {
		// n=5, one space, tight horizon: 120 schedules; ten undecided pairs
		// vs conde's 3^10 alternative worlds
		List<Strip> r = sameSpace(5);
		long resident = steps(
				schedule(r, 5, SchedulingBenchmarkTest::nonOverlapResident), starts(r));

		List<Strip> c = sameSpace(5);
		long conde = steps(
				schedule(c, 5, SchedulingBenchmarkTest::nonOverlapConde), starts(c));

		// residence LOSES here, 4.35x, and honestly: with no chains there is
		// no external knowledge — nothing decides an alternative before
		// labelling, so the store pays wholesale re-verification (packaged
		// partition, scratch impositions, no watched literals) the whole way
		// and the ground floor expands the same product anyway. Deferral
		// without decidability is overhead — the economics note's kill
		// regime, measured. The job-shop receipt above is the other pole.
		assertThat(resident).isEqualTo(224_215L);
		// conde varies a few steps across JVMs (the identity-hash iteration
		// class, substitutions-migration §5 candidate 0); range-pinned
		assertThat(conde).isBetween(51_400L, 51_700L);
	}

}
