package com.tgac.logic.finitedomain;

// ABOUTME: The typed fronts' receipts: each front pins its type's seats — dense
// ABOUTME: decimals propagate but refuse labelling, dates label, instants compare.

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.TestSchedulers;
import com.tgac.logic.Utils;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
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
	public void addoComputesTheThirdFromTwoGround() {
		// tight bounds make [2+3, 2+3] a point, and the store's collapse
		// infers the binding — no labelling, no surviving constraint
		Unifiable<Integer> c = lvar();
		Package p = imposed(dom(c, Ints.interval(0, 100)), Package.empty());

		Package summed = imposed(Ints.addo(lval(2), lval(3), c), p);

		Assertions.assertThat(summed.walk(c).get()).isEqualTo(5);
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(summed)).isEmpty();
	}

	@Test
	public void addoComputesBackwardsFromSumAndAddend() {
		// the same functional dependency read backwards: 2 + b = 7
		Unifiable<Integer> b = lvar();
		Package p = imposed(dom(b, Ints.interval(0, 100)), Package.empty());

		Package solved = imposed(Ints.addo(lval(2), b, lval(7)), p);

		Assertions.assertThat(solved.walk(b).get()).isEqualTo(5);
	}

	@Test
	public void multoComputesTheProduct() {
		Unifiable<Integer> w = lvar();
		Package p = imposed(dom(w, Ints.interval(0, 100)), Package.empty());

		Package solved = imposed(Ints.multo(lval(6), lval(7), w), p);

		Assertions.assertThat(solved.walk(w).get()).isEqualTo(42);
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(solved)).isEmpty();
	}

	@Test
	public void divoBindsTheExactQuotient() {
		Unifiable<Integer> x = lvar();
		Package p = imposed(dom(x, Ints.interval(0, 100)), Package.empty());

		Package solved = imposed(Ints.divo(lval(6), lval(3), x), p);

		Assertions.assertThat(solved.walk(x).get()).isEqualTo(2);
	}

	@Test
	public void divoRefusesAnInexactQuotient() {
		// no integer x has 2x = 7: dividedExactly's none is a refutation,
		// discovered at propagation — not after enumerating the domain
		Unifiable<Integer> x = lvar();
		Package p = imposed(dom(x, Ints.interval(0, 100)), Package.empty());

		Assertions.assertThat(worlds(Ints.divo(lval(7), lval(2), x), p)).isEmpty();
	}

	@Test
	public void zeroTimesAnythingIsZero() {
		// 0·v = 0 holds for every v: subsumed, the domain untouched;
		// 0·v = 5 holds for none: refuted
		Unifiable<Integer> v = lvar();
		Package p = imposed(dom(v, Ints.interval(0, 100)), Package.empty());

		Package discharged = imposed(Ints.multo(lval(0), v, lval(0)), p);
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(discharged)).isEmpty();
		Assertions.assertThat(FiniteDomainConstraints.getDom(discharged, v.getVar()).get())
				.isEqualTo(Ints.interval(0, 100));

		Assertions.assertThat(worlds(Ints.multo(lval(0), v, lval(5)), p)).isEmpty();
	}

	@Test
	public void addoBindsABareThird() {
		// the is/2 tier: no domain anywhere — two points mint the third's
		// binding through the ordinary chokepoint
		Unifiable<Integer> c = lvar();

		Package summed = imposed(Ints.addo(lval(2), lval(3), c), Package.empty());

		Assertions.assertThat(summed.walk(c).get()).isEqualTo(5);
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(summed)).isEmpty();
	}

	@Test
	public void subtractoComputesTheBareSubtrahend() {
		// 5 − b = 3 read backwards through the same functional dependency
		Unifiable<Integer> b = lvar();

		Package solved = imposed(Ints.subtracto(lval(5), b, lval(3)), Package.empty());

		Assertions.assertThat(solved.walk(b).get()).isEqualTo(2);
	}

	@Test
	public void divoBindsABareResult() {
		Unifiable<Integer> x = lvar();

		Package solved = imposed(Ints.divo(lval(6), lval(3), x), Package.empty());

		Assertions.assertThat(solved.walk(x).get()).isEqualTo(2);
	}

	@Test
	public void divoRefusesABareInexactResult() {
		Unifiable<Integer> x = lvar();

		Assertions.assertThat(worlds(Ints.divo(lval(7), lval(2), x), Package.empty())).isEmpty();
	}

	@Test
	public void zeroTimesABareVariableSubsumes() {
		// 0·v = 0 for every v: discharged with v still FREE — no domain, no
		// binding, no surviving constraint
		Unifiable<Integer> v = lvar();

		Package discharged = imposed(Ints.multo(lval(0), v, lval(0)), Package.empty());

		Assertions.assertThat(discharged.walk(v).asVar().isDefined()).isTrue();
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(discharged)).isEmpty();
	}

	@Test
	public void denseSeparateCutsTheDomainAndDischarges() {
		// [0,1] − {1} = [0,1): the disequality becomes domain knowledge and
		// the constraint leaves the store instead of watching forever
		Unifiable<BigDecimal> x = lvar();
		Package p = imposed(dom(x, BigDecimals.interval(BigDecimal.ZERO, BigDecimal.ONE)),
				Package.empty());

		Package cut = imposed(BigDecimals.separate(x, lval(BigDecimal.ONE)), p);

		Domain<BigDecimal> domain = FiniteDomainConstraints.getDom(cut, x.getVar()).get();
		Assertions.assertThat(domain.contains(BigDecimal.ONE)).isFalse();
		Assertions.assertThat(domain.contains(new BigDecimal("0.5"))).isTrue();
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(cut)).isEmpty();
	}

	@Test
	public void denseStrictOrderSharpensTheBound() {
		// x < 2.5 narrows x's domain to [0, 2.5) — the excluded endpoint
		// leaves the domain while x is still wide, not only at ground
		Unifiable<BigDecimal> x = lvar();
		BigDecimal cut = new BigDecimal("2.5");
		Package p = imposed(dom(x, BigDecimals.interval(BigDecimal.ZERO, cut)),
				Package.empty());

		Package narrowed = imposed(BigDecimals.lss(x, lval(cut)), p);

		Domain<BigDecimal> domain = FiniteDomainConstraints.getDom(narrowed, x.getVar()).get();
		Assertions.assertThat(domain.contains(cut)).isFalse();
		Assertions.assertThat(domain.contains(new BigDecimal("2.4"))).isTrue();
	}

	private static Package imposed(Posting posting, Package p) {
		io.vavr.collection.List<Package> worlds = worlds(posting, p);
		Assertions.assertThat(worlds).hasSize(1);
		return worlds.head();
	}

	// io.vavr.collection.List: genuine simple-name clash with java.util.List
	private static io.vavr.collection.List<Package> worlds(Posting posting, Package p) {
		return new BreadthFirstScheduler<>(Trial.imposed(posting, p)).get();
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
