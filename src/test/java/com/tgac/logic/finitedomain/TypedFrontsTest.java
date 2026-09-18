package com.tgac.logic.finitedomain;

// ABOUTME: The typed fronts' receipts: each front pins its type's seats — dense
// ABOUTME: decimals propagate but refuse labelling, dates label, instants compare.

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.Utils;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TypedFrontsTest {

	@Test
	public void decimalAddoComputesTheThirdFromTwoGround() {
		// the float lie made true: 0.1 + 0.2 IS 0.3 in exact decimals, and the
		// tight dense bounds collapse the domained third to a binding
		Unifiable<BigDecimal> c = lvar();

		List<BigDecimal> result = Utils.collect(Goal.success()
				.and(dom(c, BigDecimals.interval(BigDecimal.ZERO, BigDecimal.ONE)))
				.and(BigDecimals.addo(
						lval(new BigDecimal("0.1")),
						lval(new BigDecimal("0.2")),
						c))
				.solve(c, TestSchedulers.factory())
				.map(Term::get));

		Assertions.assertThat(result).containsExactly(new BigDecimal("0.3"));
	}

	@Test
	public void decimalAddoVerifiesAGroundTriple() {
		List<BigDecimal> good = Utils.collect(Goal.success()
				.and(BigDecimals.addo(
						lval(new BigDecimal("2.5")),
						lval(new BigDecimal("0.25")),
						lval(new BigDecimal("2.75"))))
				.solve(lval(BigDecimal.ONE), TestSchedulers.factory())
				.map(Term::get));
		Assertions.assertThat(good).hasSize(1);

		List<BigDecimal> bad = Utils.collect(Goal.success()
				.and(BigDecimals.addo(
						lval(new BigDecimal("2.5")),
						lval(new BigDecimal("0.25")),
						lval(new BigDecimal("3"))))
				.solve(lval(BigDecimal.ONE), TestSchedulers.factory())
				.map(Term::get));
		Assertions.assertThat(bad).isEmpty();
	}

	@Test
	public void decimalOrderDecidesOnGround() {
		Assertions.assertThat(Utils.collect(Goal.success()
				.and(BigDecimals.leq(lval(new BigDecimal("2.5")), lval(new BigDecimal("2.5"))))
				.solve(lval(BigDecimal.ONE), TestSchedulers.factory()))).hasSize(1);

		Assertions.assertThat(Utils.collect(Goal.success()
				.and(BigDecimals.lss(lval(new BigDecimal("2.5")), lval(new BigDecimal("2.5"))))
				.solve(lval(BigDecimal.ONE), TestSchedulers.factory()))).isEmpty();
	}

	@Test
	public void denseIntervalRefusesLabellingLoudly() {
		Unifiable<BigDecimal> x = lvar();

		Assertions.assertThatThrownBy(() -> Utils.collect(Goal.success()
						.and(dom(x, BigDecimals.interval(BigDecimal.ZERO, BigDecimal.ONE)))
						.solve(x, TestSchedulers.factory())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Discrete");
	}

	@Test
	public void enumeratedDecimalsLabelWithoutDiscreteness() {
		// an enumerated dense domain knows its elements — labelling needs no stepping
		Unifiable<BigDecimal> x = lvar();

		List<BigDecimal> result = Utils.collect(Goal.success()
				.and(dom(x, BigDecimals.enumerated(
						new BigDecimal("0.5"), new BigDecimal("1.5"))))
				.solve(x, TestSchedulers.factory())
				.map(Term::get));

		Assertions.assertThat(result).containsExactlyInAnyOrder(
				new BigDecimal("0.5"), new BigDecimal("1.5"));
	}

	@Test
	public void dateIntervalsLabelByDays() {
		Unifiable<LocalDate> d = lvar();

		List<LocalDate> result = Utils.collect(Goal.success()
				.and(dom(d, Dates.interval(
						LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))))
				.solve(d, TestSchedulers.factory())
				.map(Term::get));

		Assertions.assertThat(result).containsExactlyInAnyOrder(
				LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 2),
				LocalDate.of(2026, 1, 3));
	}

	@Test
	public void dateOrderPrunesTheDomain() {
		Unifiable<LocalDate> d = lvar();

		List<LocalDate> result = Utils.collect(Goal.success()
				.and(dom(d, Dates.interval(
						LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))))
				.and(Dates.lss(d, lval(LocalDate.of(2026, 1, 4))))
				.solve(d, TestSchedulers.factory())
				.map(Term::get));

		Assertions.assertThat(result).containsExactlyInAnyOrder(
				LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 2),
				LocalDate.of(2026, 1, 3));
	}

	@Test
	public void instantOrderDecidesOnGround() {
		Instant earlier = Instant.parse("1969-07-20T20:17:00Z");
		Instant later = Instant.parse("2026-09-18T12:00:00Z");

		Assertions.assertThat(Utils.collect(Goal.success()
				.and(Instants.lss(lval(earlier), lval(later)))
				.solve(lval(0L), TestSchedulers.factory()))).hasSize(1);

		Assertions.assertThat(Utils.collect(Goal.success()
				.and(Instants.lss(lval(later), lval(earlier)))
				.solve(lval(0L), TestSchedulers.factory()))).isEmpty();
	}

	@Test
	public void mulFailsWhenProductBoundsMissTheDomain() {
		// u*v can only reach [4, 9]; w's domain starts at 10 — failure, not a crash
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lvar();
		Unifiable<Integer> w = lvar();

		List<Integer> result = Utils.collect(Goal.success()
				.and(dom(u, Ints.interval(2, 3)))
				.and(dom(v, Ints.interval(2, 3)))
				.and(dom(w, Ints.interval(10, 20)))
				.and(Ints.multo(u, v, w))
				.solve(w, TestSchedulers.factory())
				.map(Term::get));

		Assertions.assertThat(result).isEmpty();
	}
}
