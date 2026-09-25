package org.clauseway.logic.finitedomain.relations;

// ABOUTME: The affine add schema: point + delta = point, the positions typed by
// ABOUTME: their seats — bounds narrow all three; ground triples verify exactly.

import static org.clauseway.logic.finitedomain.relations.Operators.between;
import static org.clauseway.logic.finitedomain.relations.Operators.minus;
import static org.clauseway.logic.finitedomain.relations.Operators.plus;

import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.finitedomain.Bound;
import org.clauseway.logic.finitedomain.FiniteDomainConstraints;
import org.clauseway.logic.finitedomain.capabilities.Arithmetic;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.logic.finitedomain.domains.Interval;
import org.clauseway.logic.finitedomain.relations.Operators.VarWithDomain;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.lattice.Propagator;
import org.clauseway.logic.lattice.Verdict;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.vavr.collection.Array;
import org.clauseway.vavr.control.Option;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The one functional dependency read three ways over the whole triple
 * {@code point + delta = shifted}: the homogeneous numbers are the
 * degenerate case where both seat pairs coincide; dates plus day counts
 * are the honestly affine one, each position wearing its own order and
 * step.
 */
public final class Add extends Propagator<FiniteDomainConstraints> {

	private final Arithmetic<Object, Object> arithmetic;
	private final Comparator<Object> pointOrder;
	private final Option<Discrete<Object>> pointStep;
	private final Comparator<Object> deltaOrder;
	private final Option<Discrete<Object>> deltaStep;

	@SuppressWarnings("unchecked")
	public Add(Term<?> point, Term<?> delta, Term<?> shifted,
			Arithmetic<?, ?> arithmetic,
			Comparator<?> pointOrder, Option<? extends Discrete<?>> pointStep,
			Comparator<?> deltaOrder, Option<? extends Discrete<?>> deltaStep) {
		this(Array.of(point, delta, shifted),
				(Arithmetic<Object, Object>) arithmetic,
				(Comparator<Object>) pointOrder,
				(Option<Discrete<Object>>) (Option<?>) pointStep,
				(Comparator<Object>) deltaOrder,
				(Option<Discrete<Object>>) (Option<?>) deltaStep);
	}

	private Add(Array<? extends Term<?>> terms,
			Arithmetic<Object, Object> arithmetic,
			Comparator<Object> pointOrder, Option<Discrete<Object>> pointStep,
			Comparator<Object> deltaOrder, Option<Discrete<Object>> deltaStep) {
		super(terms);
		this.arithmetic = arithmetic;
		this.pointOrder = pointOrder;
		this.pointStep = pointStep;
		this.deltaOrder = deltaOrder;
		this.deltaStep = deltaStep;
	}

	@Override
	public Verdict propagate(Package state) {
		return Operators.gated(this::orderAt,
						(Array<VarWithDomain<Object>> vds) ->
								addVerdict(vds.get(0), vds.get(1), vds.get(2)),
						this::computedThird)
				.apply(watchedTerms(), state);
	}

	/** The delta sits in the middle; the points flank it. */
	private Comparator<Object> orderAt(int position) {
		return position == 1 ? deltaOrder : pointOrder;
	}

	/**
	 * The functional dependency read at the free position, over the others'
	 * whole domains: the hull of {@code point + delta = shifted}. Point
	 * operands make a point hull — the binding; wide ones mint the domain.
	 */
	private Verdict computedThird(Operators.SoleFree<Object> free) {
		if (free.getPosition() == 2) {
			return Operators.mintHull(free.getVariable(),
					plus(free.domainAt(0).lower(), free.domainAt(1).lower(), arithmetic),
					plus(free.domainAt(0).upper(), free.domainAt(1).upper(), arithmetic),
					pointOrder, pointStep);
		}
		if (free.getPosition() == 1) {
			return Operators.mintHull(free.getVariable(),
					between(free.domainAt(0).upper(), free.domainAt(2).lower(), arithmetic),
					between(free.domainAt(0).lower(), free.domainAt(2).upper(), arithmetic),
					deltaOrder, deltaStep);
		}
		return Operators.mintHull(free.getVariable(),
				minus(free.domainAt(2).lower(), free.domainAt(1).upper(), arithmetic),
				minus(free.domainAt(2).upper(), free.domainAt(1).lower(), arithmetic),
				pointOrder, pointStep);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new Add(terms, arithmetic, pointOrder, pointStep, deltaOrder, deltaStep);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public String name() {
		return "add";
	}

	@Override
	public Class<? extends FiniteDomainConstraints> getFactorClass() {
		return FiniteDomainConstraints.class;
	}

	private Verdict addVerdict(VarWithDomain<Object> point, VarWithDomain<Object> delta,
			VarWithDomain<Object> shifted) {
		return addVerdict(point, delta, shifted, arithmetic,
				pointOrder, pointStep, deltaOrder, deltaStep);
	}

	@SuppressWarnings("unchecked")
	static <P, V> Verdict addVerdict(
			VarWithDomain<P> point, VarWithDomain<V> delta, VarWithDomain<P> shifted,
			Arithmetic<P, V> arithmetic,
			Comparator<P> pointOrder, Option<Discrete<P>> pointStep,
			Comparator<V> deltaOrder, Option<Discrete<V>> deltaStep) {

		Bound<P> pLo = point.<P> getDomain().lower(), pUp = point.<P> getDomain().upper();
		Bound<V> dLo = delta.<V> getDomain().lower(), dUp = delta.<V> getDomain().upper();
		Bound<P> sLo = shifted.<P> getDomain().lower(), sUp = shifted.<P> getDomain().upper();

		if (point.getUnifiable().isVal() && delta.getUnifiable().isVal() && shifted.getUnifiable().isVal()) {
			// ground: check the sum exactly, nothing left to watch
			return pointOrder.compare(arithmetic.plus(pLo.getValue(), dLo.getValue()), sLo.getValue()) == 0 ?
					Verdict.subsumed() : Verdict.fail();
		}

		Interval<P> si = Interval.of(
				plus(pLo, dLo, arithmetic),
				plus(pUp, dUp, arithmetic),
				pointOrder, pointStep);

		// the delta between two points is the seat's own third reading
		Interval<V> di = Interval.of(
				between(pUp, sLo, arithmetic),
				between(pLo, sUp, arithmetic),
				deltaOrder, deltaStep);

		Interval<P> pi = Interval.of(
				minus(sLo, dUp, arithmetic),
				minus(sUp, dLo, arithmetic),
				pointOrder, pointStep);

		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(shifted.getUnifiable(), si),
						VarWithDomain.of(delta.getUnifiable(), di),
						VarWithDomain.of(point.getUnifiable(), pi))));
	}
}
