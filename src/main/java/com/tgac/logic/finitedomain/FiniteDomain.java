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
import java.util.function.BinaryOperator;
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
 * pre-specify them so no ordinary caller ever states an instance.
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
			Comparator<T> order, Option<Discrete<T>> step) {
		return Option.of(us.toJavaStream()
						.map(p::walk)
						.flatMap(v -> v.asVal()
								.map(val -> VarWithDomain.of(v, Singleton.of(v.get(), order, step)))
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
			Comparator<T> order, Option<Discrete<T>> step,
			Function<Array<VarWithDomain<T>>, Verdict> verdict) {
		return (watched, s) -> letDomain(s, FiniteDomain.<T> typed(watched), order, step)
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

	public static <T> Posting lss(Unifiable<T> less, Unifiable<T> more,
			Comparator<T> order, Option<Discrete<T>> step) {
		return Propagation.activate(new Lss(less, more, order, step));
	}

	public static <T> Posting gtr(Unifiable<T> more, Unifiable<T> less,
			Comparator<T> order, Option<Discrete<T>> step) {
		// more > less IS less < more: one sharp atom, the schema's own doom
		return Propagation.activate(new Lss(less, more, order, step));
	}

	public static <T> Posting geq(Unifiable<T> more, Unifiable<T> less, Comparator<T> order) {
		// more >= less violated ⟺ less <= more violated: the schema's own doom
		return Propagation.activate(new Leq(less, more, order));
	}

	@SuppressWarnings("unchecked")
	static <T> Verdict lssVerdict(VarWithDomain<T> lss, VarWithDomain<T> mor,
			Comparator<T> order, Option<Discrete<T>> step) {
		if (lss.getUnifiable().isVal() && mor.getUnifiable().isVal()) {
			// ground: the strict order decides exactly, dense or not
			return order.compare(lss.getUnifiable().get(), mor.getUnifiable().get()) < 0 ?
					Verdict.subsumed() : Verdict.fail();
		}
		// stepping sharpens the strict bounds; a dense type narrows non-strictly
		Domain<T> lessDom = step
				.map(d -> lss.<T> getDomain().atMost(d.prev(mor.<T> getDomain().max())))
				.getOrElse(() -> lss.<T> getDomain().atMost(mor.<T> getDomain().max()));
		Domain<T> moreDom = step
				.map(d -> mor.<T> getDomain().atLeast(d.next(lss.<T> getDomain().min())))
				.getOrElse(() -> mor.<T> getDomain().atLeast(lss.<T> getDomain().min()));
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
		Domain<T> lessDom = lss.<T> getDomain().atMost(mor.<T> getDomain().max());
		Domain<T> moreDom = mor.<T> getDomain().atLeast(lss.<T> getDomain().min());
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
			Arithmetic<T, T> arithmetic, Comparator<T> order, Option<Discrete<T>> step,
			T uMin, T vMin, T wMin,
			T uMax, T vMax, T wMax) {

		if (u.getUnifiable().isVal() && v.getUnifiable().isVal() && w.getUnifiable().isVal()) {
			// ground: check the sum exactly, nothing left to watch
			return order.compare(arithmetic.plus(uMin, vMin), wMin) == 0 ? Verdict.subsumed() : Verdict.fail();
		}

		Interval<T> wi = Interval.of(
				arithmetic.plus(uMin, vMin),
				widened(arithmetic.plus(uMax, vMax), step),
				order, step);

		Interval<T> vi = Interval.of(
				arithmetic.minus(wMin, uMax),
				widened(arithmetic.minus(wMax, uMin), step),
				order, step);

		Interval<T> ui = Interval.of(
				arithmetic.minus(wMin, vMax),
				widened(arithmetic.minus(wMax, vMin), step),
				order, step);

		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wi),
						VarWithDomain.of(v.getUnifiable(), vi),
						VarWithDomain.of(u.getUnifiable(), ui))));
	}

	/** The discrete upper bound rides one step wide; a dense bound is already exact. */
	private static <T> T widened(T bound, Option<Discrete<T>> step) {
		return step.map(d -> d.next(bound)).getOrElse(bound);
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
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step,
			T uMin, T vMin, T wMin,
			T uMax, T vMax, T wMax) {
		// all are numbers -> check multiplication
		if (order.compare(uMin, uMax) == 0 && order.compare(vMin, vMax) == 0 && order.compare(wMin, wMax) == 0) {
			return order.compare(multiplicative.times(uMin, vMin), wMin) == 0 ? Verdict.subsumed() : Verdict.fail();
		}

		// some are numbers -> do nothing until all generated
		if (order.compare(uMin, uMax) == 0 || order.compare(vMin, vMax) == 0 || order.compare(wMin, wMax) == 0) {
			return Verdict.keep();
		}

		// Trim domains
		Domain<T> wi, ui, vi;

		Array<Tuple2<T, T>> uvPerm = Array.of(
				Tuple.of(uMin, vMin),
				Tuple.of(uMax, vMin),
				Tuple.of(uMin, vMax),
				Tuple.of(uMax, vMax));

		wi = Interval.normalized(
						minResult(multiplicative::times, uvPerm, order),
						maxResult(multiplicative::times, uvPerm, order),
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
		ui = quotientBounds(wMin, wMax, vMin, vMax, multiplicative, order, step).getOrElse(() -> u.<T> getDomain());
		vi = quotientBounds(wMin, wMax, uMin, uMax, multiplicative, order, step).getOrElse(() -> v.<T> getDomain());

		Domain<T> wiF = wi, uiF = ui, viF = vi;
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wiF),
						VarWithDomain.of(u.getUnifiable(), uiF),
						VarWithDomain.of(v.getUnifiable(), viF))));
	}

	/**
	 * Bounds of {@code w / d} over the endpoint box, defined only when the
	 * divisor interval is sign-constant (no zero inside) AND every endpoint
	 * quotient divides exactly — an inexact endpoint would need directed
	 * rounding, which no seat provides, so the trim is skipped: sound, wider.
	 */
	private static <T> Option<Domain<T>> quotientBounds(
			T wMin, T wMax, T dMin, T dMax,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		T zero = multiplicative.zero();
		if (order.compare(dMin, zero) <= 0 && order.compare(dMax, zero) >= 0) {
			return Option.none();
		}
		Array<Tuple2<T, T>> wdPerm = Array.of(
				Tuple.of(wMin, dMin),
				Tuple.of(wMin, dMax),
				Tuple.of(wMax, dMin),
				Tuple.of(wMax, dMax));
		return wdPerm.toJavaStream()
				.map(t -> t.apply(multiplicative::dividedExactly))
				.reduce(Option.of(Array.<T> empty()),
						(acc, q) -> acc.flatMap(qs -> q.map(qs::append)),
						(l, r) -> l.flatMap(ls -> r.map(ls::appendAll)))
				.map(quotients -> Interval.normalized(
						quotients.toJavaStream().min(order).get(),
						quotients.toJavaStream().max(order).get(),
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

	private static <T> T minResult(BinaryOperator<T> f, Array<Tuple2<T, T>> args, Comparator<T> order) {
		return args.toJavaStream()
				.map(t -> t.apply(f))
				.min(order)
				.orElseThrow(IllegalStateException::new);
	}

	private static <T> T maxResult(BinaryOperator<T> f, Array<Tuple2<T, T>> args, Comparator<T> order) {
		return args.toJavaStream()
				.map(t -> t.apply(f))
				.max(order)
				.orElseThrow(IllegalStateException::new);
	}

}
