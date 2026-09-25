package org.clauseway.logic.finitedomain;

// ABOUTME: The typed fronts' receipts: each front pins its type's seats — dense
// ABOUTME: decimals propagate but refuse labelling, dates label, instants compare.

import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.TestSchedulers;
import org.clauseway.logic.Utils;
import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.constraints.Trial;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple4;
import java.math.BigDecimal;
import java.time.Duration;
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
	public void dateAddoShiftsByDays() {
		// the first genuinely affine triple: point + delta = point, the
		// positions typed differently — a date, a day count, a date
		Unifiable<LocalDate> due = lvar();

		Package solved = imposed(Dates.addo(
				lval(LocalDate.of(2026, 1, 1)), lval(14L), due), Package.empty());

		Assertions.assertThat(solved.walk(due).get()).isEqualTo(LocalDate.of(2026, 1, 15));
	}

	@Test
	public void dateAddoComputesTheLength() {
		// the between reading: two dates determine the day count
		Unifiable<Long> days = lvar();

		Package solved = imposed(Dates.addo(
				lval(LocalDate.of(2026, 1, 1)), days, lval(LocalDate.of(2026, 1, 15))), Package.empty());

		Assertions.assertThat(solved.walk(days).get()).isEqualTo(14L);
	}

	@Test
	public void dateAddoComputesTheStart() {
		Unifiable<LocalDate> day = lvar();

		Package solved = imposed(Dates.addo(
				day, lval(14L), lval(LocalDate.of(2026, 1, 15))), Package.empty());

		Assertions.assertThat(solved.walk(day).get()).isEqualTo(LocalDate.of(2026, 1, 1));
	}

	@Test
	public void dateAddoMintsTheDueWindow() {
		// wide date × wide day count: the due window's hull, minted
		Unifiable<LocalDate> day = lvar();
		Unifiable<Long> len = lvar();
		Unifiable<LocalDate> due = lvar();
		Package p = imposed(dom(day, Dates.interval(
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))), Package.empty());
		p = imposed(dom(len, Longs.interval(10, 14)), p);

		Package minted = imposed(Dates.addo(day, len, due), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(minted, due.getVar()).get())
				.isEqualTo(Dates.interval(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 19)));
	}

	@Test
	public void dateAddoVerifiesAGroundTriple() {
		Assertions.assertThat(worlds(Dates.addo(
				lval(LocalDate.of(2026, 1, 1)), lval(14L), lval(LocalDate.of(2026, 1, 15))),
				Package.empty())).hasSize(1);
		Assertions.assertThat(worlds(Dates.addo(
				lval(LocalDate.of(2026, 1, 1)), lval(14L), lval(LocalDate.of(2026, 1, 16))),
				Package.empty())).isEmpty();
	}

	@Test
	public void instantAddoShiftsByDuration() {
		Unifiable<Instant> later = lvar();

		Package solved = imposed(Instants.addo(
				lval(Instant.EPOCH), lval(Duration.ofHours(2)), later), Package.empty());

		Assertions.assertThat(solved.walk(later).get())
				.isEqualTo(Instant.parse("1970-01-01T02:00:00Z"));
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
	public void addoMintsTheHullOfAFreeThird() {
		// mode (b): wide operands mint the free position's domain — the
		// constraint contributes knowledge before anything labels
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();
		Package p = imposed(dom(a, Ints.interval(0, 5)), Package.empty());
		p = imposed(dom(b, Ints.interval(0, 5)), p);

		Package minted = imposed(Ints.addo(a, b, c), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(minted, c.getVar()).get())
				.isEqualTo(Ints.interval(0, 10));
	}

	@Test
	public void addoChainMintsThroughIntermediates() {
		// a+b=t1, t1+c=t2, t2+d=e: every intermediate and the result get
		// their hulls minted, none was ever declared
		Unifiable<Integer> a = lvar(), b = lvar(), c = lvar(), d = lvar();
		Unifiable<Integer> t1 = lvar(), t2 = lvar(), e = lvar();
		Package p = domained(Package.empty(), a, b, c, d);

		p = imposed(Ints.addo(a, b, t1), p);
		p = imposed(Ints.addo(t1, c, t2), p);
		p = imposed(Ints.addo(t2, d, e), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(p, t1.getVar()).get())
				.isEqualTo(Ints.interval(0, 10));
		Assertions.assertThat(FiniteDomainConstraints.getDom(p, t2.getVar()).get())
				.isEqualTo(Ints.interval(0, 15));
		Assertions.assertThat(FiniteDomainConstraints.getDom(p, e.getVar()).get())
				.isEqualTo(Ints.interval(0, 20));
	}

	@Test
	public void addoChainMintsAgainstStatementOrder() {
		// the chain stated back to front: the later links park with two free
		// positions, and each mint's re-examination note wakes them awake —
		// minting rides the cascade, not the statement order
		Unifiable<Integer> a = lvar(), b = lvar(), c = lvar(), d = lvar();
		Unifiable<Integer> t1 = lvar(), t2 = lvar(), e = lvar();
		Package p = domained(Package.empty(), a, b, c, d);

		p = imposed(Ints.addo(t2, d, e), p);
		p = imposed(Ints.addo(t1, c, t2), p);
		p = imposed(Ints.addo(a, b, t1), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(p, t1.getVar()).get())
				.isEqualTo(Ints.interval(0, 10));
		Assertions.assertThat(FiniteDomainConstraints.getDom(p, t2.getVar()).get())
				.isEqualTo(Ints.interval(0, 15));
		Assertions.assertThat(FiniteDomainConstraints.getDom(p, e.getVar()).get())
				.isEqualTo(Ints.interval(0, 20));
	}

	@Test
	public void addoChainPrunesThroughMintedDomains() {
		// the minted intermediates carry constraint knowledge backwards:
		// e < 2 prunes a, b, c, d through the chain, and the answers are
		// exactly the tuples summing below 2
		Unifiable<Integer> a = lvar(), b = lvar(), c = lvar(), d = lvar();
		Unifiable<Integer> t1 = lvar(), t2 = lvar(), e = lvar();

		List<Tuple4<Integer, Integer, Integer, Integer>> result =
				Utils.collect(Goal.success()
						.and(dom(a, Ints.interval(0, 5)))
						.and(dom(b, Ints.interval(0, 5)))
						.and(dom(c, Ints.interval(0, 5)))
						.and(dom(d, Ints.interval(0, 5)))
						.and(Ints.addo(a, b, t1))
						.and(Ints.addo(t1, c, t2))
						.and(Ints.addo(t2, d, e))
						.and(Ints.lss(e, lval(2)))
						.solve(lval(Tuple.of(a, b, c, d)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t.map(Term::get, Term::get, Term::get, Term::get)));

		Assertions.assertThat(result)
				.hasSize(5)
				.allMatch(t -> t._1 + t._2 + t._3 + t._4 < 2);
	}

	@SafeVarargs
	private static Package domained(Package p, Unifiable<Integer>... vars) {
		for (Unifiable<Integer> v : vars) {
			p = imposed(dom(v, Ints.interval(0, 5)), p);
		}
		return p;
	}

	@Test
	public void multoMintsTheProductHull() {
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lvar();
		Unifiable<Integer> w = lvar();
		Package p = imposed(dom(u, Ints.interval(2, 3)), Package.empty());
		p = imposed(dom(v, Ints.interval(2, 3)), p);

		Package minted = imposed(Ints.multo(u, v, w), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(minted, w.getVar()).get())
				.isEqualTo(Ints.interval(4, 9));
	}

	@Test
	public void multoMintsAnExactQuotientHull() {
		// every endpoint of [4,8] / [2,4] divides exactly: the factor's hull
		// is mintable without any rounding seat
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lvar();
		Unifiable<Integer> w = lvar();
		Package p = imposed(dom(v, Ints.interval(2, 4)), Package.empty());
		p = imposed(dom(w, Ints.interval(4, 8)), p);

		Package minted = imposed(Ints.multo(u, v, w), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(minted, u.getVar()).get())
				.isEqualTo(Ints.interval(1, 4));
	}

	@Test
	public void multoKeepsTheFactorWhenTheDivisorSpansZero() {
		// w/v is unbounded around v = 0: no hull exists, the constraint
		// parks and waits — no mint, no lie
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lvar();
		Unifiable<Integer> w = lvar();
		Package p = imposed(dom(v, Ints.interval(-1, 1)), Package.empty());
		p = imposed(dom(w, Ints.interval(4, 8)), p);

		Package kept = imposed(Ints.multo(u, v, w), p);

		Assertions.assertThat(FiniteDomainConstraints.getDom(kept, u.getVar())).isEmpty();
		Assertions.assertThat(FiniteDomainConstraints.getConstraints(kept)).hasSize(1);
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

		Assertions.assertThat(discharged.walk(v).asVar().isPresent()).isTrue();
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
