package com.tgac.logic.finitedomain.relations;

// ABOUTME: The mul schema: a * b = rhs — interval bounds narrow all three
// ABOUTME: positions; ground triples verify exactly.

import static com.tgac.logic.finitedomain.relations.Operators.highest;
import static com.tgac.logic.finitedomain.relations.Operators.lowest;
import static com.tgac.logic.finitedomain.relations.Operators.times;

import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.finitedomain.relations.Operators.VarWithDomain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public final class Mul extends Propagator<FiniteDomainConstraints> {

	private final Multiplicative<Object> multiplicative;
	private final Comparator<Object> order;
	private final Option<Discrete<Object>> step;

	@SuppressWarnings("unchecked")
	public Mul(Term<?> a, Term<?> b, Term<?> rhs,
			Multiplicative<?> multiplicative, Comparator<?> order, Option<? extends Discrete<?>> step) {
		this(Array.of(a, b, rhs),
				(Multiplicative<Object>) multiplicative,
				(Comparator<Object>) order,
				(Option<Discrete<Object>>) step);
	}

	private Mul(Array<? extends Term<?>> terms,
			Multiplicative<Object> multiplicative, Comparator<Object> order, Option<Discrete<Object>> step) {
		super(terms);
		this.multiplicative = multiplicative;
		this.order = order;
		this.step = step;
	}

	@Override
	public Verdict propagate(Package state) {
		return Operators.gated(order,
						(Array<VarWithDomain<Object>> vds) ->
								Tuple.of(vds.get(0), vds.get(1), vds.get(2))
										.apply((u, v, w) -> mulVerdict(u, v, w,
												multiplicative, order, step)),
						this::computedThird)
				.apply(watchedTerms(), state);
	}

	/** The functional dependency read at the free position: a · b = rhs. */
	private Verdict computedThird(Operators.SoleFree<Object> free) {
		if (free.getPosition() == 2) {
			return Operators.narrowToPoint(free.getVariable(),
					multiplicative.times(free.pointAt(0), free.pointAt(1)), order, step);
		}
		return free.getPosition() == 1 ?
				solveFactor(free.getVariable(), free.pointAt(0), free.pointAt(2), multiplicative, order, step) :
				solveFactor(free.getVariable(), free.pointAt(1), free.pointAt(2), multiplicative, order, step);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new Mul(terms, multiplicative, order, step);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public String name() {
		return "mul";
	}

	@Override
	public Class<? extends FiniteDomainConstraints> getFactorClass() {
		return FiniteDomainConstraints.class;
	}

	@SuppressWarnings("unchecked")
	static <T> Verdict mulVerdict(
			VarWithDomain<T> u, VarWithDomain<T> v, VarWithDomain<T> w,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {

		Bound<T> uLo = u.<T> getDomain().lower(), uUp = u.<T> getDomain().upper();
		Bound<T> vLo = v.<T> getDomain().lower(), vUp = v.<T> getDomain().upper();
		Bound<T> wLo = w.<T> getDomain().lower(), wUp = w.<T> getDomain().upper();

		boolean uPoint = order.compare(uLo.getValue(), uUp.getValue()) == 0;
		boolean vPoint = order.compare(vLo.getValue(), vUp.getValue()) == 0;
		boolean wPoint = order.compare(wLo.getValue(), wUp.getValue()) == 0;

		// all are points -> check multiplication
		if (uPoint && vPoint && wPoint) {
			return order.compare(multiplicative.times(uLo.getValue(), vLo.getValue()), wLo.getValue()) == 0 ?
					Verdict.subsumed() : Verdict.fail();
		}

		// two points determine the third: the product directly, a factor
		// through the exact-or-refuse inverse
		if (uPoint && vPoint) {
			return Operators.narrowToPoint(w.getUnifiable(),
					multiplicative.times(uLo.getValue(), vLo.getValue()), order, step);
		}
		if (uPoint && wPoint) {
			return solveFactor(v.getUnifiable(), uLo.getValue(), wLo.getValue(), multiplicative, order, step);
		}
		if (vPoint && wPoint) {
			return solveFactor(u.getUnifiable(), vLo.getValue(), wLo.getValue(), multiplicative, order, step);
		}

		// one point -> no bounds trim until the others narrow
		if (uPoint || vPoint || wPoint) {
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
			return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
					(Theory<FiniteDomainConstraints>) theory,
					Collections.singletonList(
							VarWithDomain.of(w.getUnifiable(), wi))));
		}

		// quotient bounds are meaningless when the divisor interval spans zero
		// (w/v is unbounded around v = 0) — trim only sign-constant divisors
		ui = quotientBounds(wLo, wUp, vLo, vUp, multiplicative, order, step).getOrElse(u::getDomain);
		vi = quotientBounds(wLo, wUp, uLo, uUp, multiplicative, order, step).getOrElse(v::getDomain);

		Domain<T> wiF = wi, uiF = ui, viF = vi;
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.asList(
						VarWithDomain.of(w.getUnifiable(), wiF),
						VarWithDomain.of(u.getUnifiable(), uiF),
						VarWithDomain.of(v.getUnifiable(), viF))));
	}

	/**
	 * The factor {@code x} with {@code x · factor = product}: the zero cases
	 * split first — {@code 0 · x = 0} holds for every x (subsumed) and
	 * {@code 0 · x ≠ 0} for none — then {@code dividedExactly} answers the
	 * unique factor or REFUTES the triple.
	 */
	private static <T> Verdict solveFactor(Term<T> side, T factor, T product,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		if (order.compare(factor, multiplicative.zero()) == 0) {
			return order.compare(product, multiplicative.zero()) == 0 ?
					Verdict.subsumed() : Verdict.fail();
		}
		return multiplicative.dividedExactly(product, factor)
				.map(quotient -> Operators.narrowToPoint(side, quotient, order, step))
				.getOrElse(Verdict::fail);
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
}
