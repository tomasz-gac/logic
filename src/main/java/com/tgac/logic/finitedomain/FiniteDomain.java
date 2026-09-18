package com.tgac.logic.finitedomain;

// ABOUTME: The instance-explicit FD core: domain membership, order and arithmetic
// ABOUTME: schemas over any type that brings its capability seats.

import com.tgac.functional.reflection.Types;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * The generic front door: every operation takes the capability seats it
 * needs as plain arguments — the typed fronts ({@link Ints}, {@link Longs},
 * {@link BigIntegers}, {@link BigDecimals}, {@link Dates}, {@link Instants})
 * pre-specify them so no ordinary caller ever states an instance. Strict
 * orders narrow through OPEN bounds ({@link Bound}); stepping plays no part
 * in comparison — a domain with a step seat normalizes the open bound to
 * its closed spelling itself.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FiniteDomain {

	/** The membership {@code u ∈ d} through the store's imposition door. */
	@SuppressWarnings("unchecked")
	public static <T> Posting dom(Unifiable<T> u, Domain<T> d) {
		return FiniteDomainConstraints.empty().impose(u, (Domain<Object>) d);
	}

	static long cmpOrder(Substitutions s, Term<?> l, Term<?> r, IntPredicate satisfied, Comparator<Object> order) {
		Term<?> lw = s.walk(l);
		Term<?> rw = s.walk(r);
		if (lw.asVal().isDefined() && rw.asVal().isDefined()) {
			return satisfied.test(order.compare(lw.get(), rw.get())) ? 1 : 0;
		}
		return 1;
	}

	static <T> Option<Array<VarWithDomain<T>>> letDomain(Package p, Array<? extends Term<T>> us,
			Comparator<T> order) {
		return Option.of(us.toJavaStream()
						.map(p::walk)
						.flatMap(v -> v.asVal()
								.map(val -> VarWithDomain.of(v,
										Singleton.of(v.get(), order, Option.<Discrete<T>> none())))
								.map(Stream::of)
								.getOrElse(() -> FiniteDomainConstraints.getDom(p, v.getVar())
										.map(d -> VarWithDomain.of(v, d))
										.toJavaStream()))
						.collect(Array.collector()))
				.filter(uds -> uds.size() == us.size());
	}

	@SuppressWarnings("unchecked")
	static <T> Array<? extends Term<T>> typed(Array<? extends Term<?>> watched) {
		return (Array<? extends Term<T>>) watched;
	}

	static <T> BiFunction<Array<? extends Term<?>>, Package, Verdict> gated(
			Comparator<T> order,
			Function<Array<VarWithDomain<T>>, Verdict> verdict) {
		return (watched, s) -> letDomain(s, FiniteDomain.<T> typed(watched), order)
				.filter(uds -> uds.toJavaStream()
						.noneMatch(ud -> ud.getDomain().isEmpty()))
				.map(verdict)
				.getOrElse(Verdict::keep);
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	static class VarWithDomain<T> {
		Term<T> unifiable;
		Domain<?> domain;

		@SuppressWarnings("unchecked")
		public <U> Domain<U> getDomain() {
			return (Domain<U>) domain;
		}
	}

	public static <T> Posting leq(Unifiable<T> less, Unifiable<T> more, Comparator<T> order) {
		return Propagation.activate(new Leq(less, more, order));
	}

	public static <T> Posting lss(Unifiable<T> less, Unifiable<T> more, Comparator<T> order) {
		return Propagation.activate(new Lss(less, more, order));
	}

	public static <T> Posting gtr(Unifiable<T> more, Unifiable<T> less, Comparator<T> order) {
		// more > less IS less < more: one sharp atom, the schema's own doom
		return Propagation.activate(new Lss(less, more, order));
	}

	public static <T> Posting geq(Unifiable<T> more, Unifiable<T> less, Comparator<T> order) {
		// more >= less violated ⟺ less <= more violated: the schema's own doom
		return Propagation.activate(new Leq(less, more, order));
	}

	@SuppressWarnings("unchecked")
	static <T> Verdict lssVerdict(VarWithDomain<T> lss, VarWithDomain<T> mor, Comparator<T> order) {
		if (lss.getUnifiable().isVal() && mor.getUnifiable().isVal()) {
			// ground: the strict order decides exactly
			return order.compare(lss.getUnifiable().get(), mor.getUnifiable().get()) < 0 ?
					Verdict.subsumed() : Verdict.fail();
		}
		// the strict bound is the other side's bound with its point excluded
		Domain<T> lessDom = lss.<T> getDomain().atMost(mor.<T> getDomain().upper().opened());
		Domain<T> moreDom = mor.<T> getDomain().atLeast(lss.<T> getDomain().lower().opened());
		if (lessDom.isEmpty() || moreDom.isEmpty()) {
			return Verdict.fail();
		}
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(lss.getUnifiable(), lessDom),
						VarWithDomain.of(mor.getUnifiable(), moreDom))));
	}

	@SuppressWarnings("unchecked")
	static <T> Verdict leqVerdict(VarWithDomain<T> lss, VarWithDomain<T> mor, Comparator<T> order) {
		if (lss.getUnifiable().isVal() && mor.getUnifiable().isVal()) {
			// ground: the order decides exactly, nothing left to watch
			return order.compare(lss.getUnifiable().get(), mor.getUnifiable().get()) <= 0 ?
					Verdict.subsumed() : Verdict.fail();
		}
		Domain<T> lessDom = lss.<T> getDomain().atMost(mor.<T> getDomain().upper());
		Domain<T> moreDom = mor.<T> getDomain().atLeast(lss.<T> getDomain().lower());
		if (lessDom.isEmpty() || moreDom.isEmpty()) {
			return Verdict.fail();
		}
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(lss.getUnifiable(), lessDom),
						VarWithDomain.of(mor.getUnifiable(), moreDom))));
	}

	public static <T> Posting addo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c,
			Arithmetic<T, T> arithmetic, Comparator<T> order, Option<Discrete<T>> step) {
		return Propagation.activate(new Add(a, b, c, arithmetic, order, step));
	}

	public static <T> Posting subtracto(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c,
			Arithmetic<T, T> arithmetic, Comparator<T> order, Option<Discrete<T>> step) {
		return addo(c, b, a, arithmetic, order, step);
	}

	@SuppressWarnings("unchecked")
	static <T> Verdict addVerdict(
			VarWithDomain<T> u, VarWithDomain<T> v, VarWithDomain<T> w,
			Arithmetic<T, T> arithmetic, Comparator<T> order, Option<Discrete<T>> step) {

		Bound<T> uLo = u.<T> getDomain().lower(), uUp = u.<T> getDomain().upper();
		Bound<T> vLo = v.<T> getDomain().lower(), vUp = v.<T> getDomain().upper();
		Bound<T> wLo = w.<T> getDomain().lower(), wUp = w.<T> getDomain().upper();

		if (u.getUnifiable().isVal() && v.getUnifiable().isVal() && w.getUnifiable().isVal()) {
			// ground: check the sum exactly, nothing left to watch
			return order.compare(arithmetic.plus(uLo.getValue(), vLo.getValue()), wLo.getValue()) == 0 ?
					Verdict.subsumed() : Verdict.fail();
		}

		Interval<T> wi = Interval.of(
				plus(uLo, vLo, arithmetic),
				widened(plus(uUp, vUp, arithmetic), step),
				order, step);

		Interval<T> vi = Interval.of(
				minus(wLo, uUp, arithmetic),
				widened(minus(wUp, uLo, arithmetic), step),
				order, step);

		Interval<T> ui = Interval.of(
				minus(wLo, vUp, arithmetic),
				widened(minus(wUp, vLo, arithmetic), step),
				order, step);

		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wi),
						VarWithDomain.of(v.getUnifiable(), vi),
						VarWithDomain.of(u.getUnifiable(), ui))));
	}

	private static <T> Bound<T> plus(Bound<T> a, Bound<T> b, Arithmetic<T, T> arithmetic) {
		return new Bound<>(arithmetic.plus(a.getValue(), b.getValue()),
				a.isIncluded() && b.isIncluded());
	}

	private static <T> Bound<T> minus(Bound<T> a, Bound<T> b, Arithmetic<T, T> arithmetic) {
		return new Bound<>(arithmetic.minus(a.getValue(), b.getValue()),
				a.isIncluded() && b.isIncluded());
	}

	/** The discrete upper bound rides one step wide; a dense bound is already exact. */
	private static <T> Bound<T> widened(Bound<T> bound, Option<Discrete<T>> step) {
		return step.map(d -> Bound.closed(d.next(bound.getValue()))).getOrElse(bound);
	}

	public static <T> Posting multo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		return Propagation.activate(new Mul(a, b, c, multiplicative, order, step));
	}

	public static <T> Posting divo(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> result,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		return multo(result, divisor, divided, multiplicative, order, step);
	}

	@SuppressWarnings("unchecked")
	static <T> Verdict mulVerdict(
			VarWithDomain<T> u, VarWithDomain<T> v, VarWithDomain<T> w,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {

		Bound<T> uLo = u.<T> getDomain().lower(), uUp = u.<T> getDomain().upper();
		Bound<T> vLo = v.<T> getDomain().lower(), vUp = v.<T> getDomain().upper();
		Bound<T> wLo = w.<T> getDomain().lower(), wUp = w.<T> getDomain().upper();

		// all are numbers -> check multiplication
		if (order.compare(uLo.getValue(), uUp.getValue()) == 0
				&& order.compare(vLo.getValue(), vUp.getValue()) == 0
				&& order.compare(wLo.getValue(), wUp.getValue()) == 0) {
			return order.compare(multiplicative.times(uLo.getValue(), vLo.getValue()), wLo.getValue()) == 0 ?
					Verdict.subsumed() : Verdict.fail();
		}

		// some are numbers -> do nothing until all generated
		if (order.compare(uLo.getValue(), uUp.getValue()) == 0
				|| order.compare(vLo.getValue(), vUp.getValue()) == 0
				|| order.compare(wLo.getValue(), wUp.getValue()) == 0) {
			return Verdict.keep();
		}

		// Trim domains
		Domain<T> wi, ui, vi;

		Array<Bound<T>> products = Array.of(
				times(uLo, vLo, multiplicative),
				times(uUp, vLo, multiplicative),
				times(uLo, vUp, multiplicative),
				times(uUp, vUp, multiplicative));

		wi = Interval.of(
						lowest(products, order),
						highest(products, order),
						order, step)
				.intersect(w.getDomain());

		if (wi.isEmpty()) {
			return Verdict.fail();
		}

		// result is zero, so we cannot infer any u or v bounds information
		if (order.compare(wi.min(), wi.max()) == 0 && order.compare(wi.min(), multiplicative.zero()) == 0) {
			Domain<T> wiZero = wi;
			return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
					(Theory<FiniteDomainConstraints>) theory,
					Collections.<VarWithDomain<?>> singletonList(
							VarWithDomain.of(w.getUnifiable(), wiZero))));
		}

		// quotient bounds are meaningless when the divisor interval spans zero
		// (w/v is unbounded around v = 0) — trim only sign-constant divisors
		ui = quotientBounds(wLo, wUp, vLo, vUp, multiplicative, order, step).getOrElse(() -> u.<T> getDomain());
		vi = quotientBounds(wLo, wUp, uLo, uUp, multiplicative, order, step).getOrElse(() -> v.<T> getDomain());

		Domain<T> wiF = wi, uiF = ui, viF = vi;
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wiF),
						VarWithDomain.of(u.getUnifiable(), uiF),
						VarWithDomain.of(v.getUnifiable(), viF))));
	}

	private static <T> Bound<T> times(Bound<T> a, Bound<T> b, Multiplicative<T> multiplicative) {
		return new Bound<>(multiplicative.times(a.getValue(), b.getValue()),
				a.isIncluded() && b.isIncluded());
	}

	/**
	 * Bounds of {@code w / d} over the endpoint box, defined only when the
	 * divisor interval is sign-constant (no zero inside) AND every endpoint
	 * quotient divides exactly — an inexact endpoint would need directed
	 * rounding, which no seat provides, so the trim is skipped: sound, wider.
	 */
	private static <T> Option<Domain<T>> quotientBounds(
			Bound<T> wLo, Bound<T> wUp, Bound<T> dLo, Bound<T> dUp,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		T zero = multiplicative.zero();
		if (order.compare(dLo.getValue(), zero) <= 0 && order.compare(dUp.getValue(), zero) >= 0) {
			return Option.none();
		}
		Array<Tuple2<Bound<T>, Bound<T>>> wdPerm = Array.of(
				Tuple.of(wLo, dLo),
				Tuple.of(wLo, dUp),
				Tuple.of(wUp, dLo),
				Tuple.of(wUp, dUp));
		return wdPerm.toJavaStream()
				.map(t -> t.apply((wb, db) -> multiplicative
						.dividedExactly(wb.getValue(), db.getValue())
						.map(q -> new Bound<>(q, wb.isIncluded() && db.isIncluded()))))
				.reduce(Option.of(Array.<Bound<T>> empty()),
						(acc, q) -> acc.flatMap(qs -> q.map(qs::append)),
						(l, r) -> l.flatMap(ls -> r.map(ls::appendAll)))
				.map(quotients -> Interval.of(
						lowest(quotients, order),
						highest(quotients, order),
						order, step));
	}

	public static <T> Posting separate(Unifiable<T> l, Unifiable<T> r, Comparator<T> order) {
		return Propagation.activate(new Separate(l, r, order));
	}

	static <T> Option<T> getSingleElement(Domain<T> dom) {
		return Option.of(dom)
				.flatMap(Types.<Singleton<T>> castAs(Singleton.class))
				.map(Singleton::getValue);
	}

	/** The hull's lower bound: least value; at a tie an attained (closed) endpoint wins. */
	private static <T> Bound<T> lowest(Array<Bound<T>> candidates, Comparator<T> order) {
		return candidates.reduce((a, b) -> {
			int c = order.compare(a.getValue(), b.getValue());
			if (c != 0) {
				return c < 0 ? a : b;
			}
			return a.isIncluded() ? a : b;
		});
	}

	/** The hull's upper bound: greatest value; at a tie an attained (closed) endpoint wins. */
	private static <T> Bound<T> highest(Array<Bound<T>> candidates, Comparator<T> order) {
		return candidates.reduce((a, b) -> {
			int c = order.compare(a.getValue(), b.getValue());
			if (c != 0) {
				return c > 0 ? a : b;
			}
			return a.isIncluded() ? a : b;
		});
	}

}
