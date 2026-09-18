package com.tgac.logic.finitedomain.relations;

// ABOUTME: The add schema: a + b = rhs — interval bounds narrow all three
// ABOUTME: positions; ground triples verify exactly.

import static com.tgac.logic.finitedomain.relations.Operators.minus;

import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.relations.Operators.VarWithDomain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Comparator;

public final class Add extends Propagator<FiniteDomainConstraints> {

	private final Arithmetic<Object, Object> arithmetic;
	private final Comparator<Object> order;
	private final Option<Discrete<Object>> step;

	@SuppressWarnings("unchecked")
	public Add(Term<?> a, Term<?> b, Term<?> rhs,
			Arithmetic<?, ?> arithmetic, Comparator<?> order, Option<? extends Discrete<?>> step) {
		this(Array.of(a, b, rhs),
				(Arithmetic<Object, Object>) arithmetic,
				(Comparator<Object>) order,
				(Option<Discrete<Object>>) (Option<?>) step);
	}

	private Add(Array<? extends Term<?>> terms,
			Arithmetic<Object, Object> arithmetic, Comparator<Object> order, Option<Discrete<Object>> step) {
		super(terms);
		this.arithmetic = arithmetic;
		this.order = order;
		this.step = step;
	}

	@Override
	public Verdict propagate(Package state) {
		return Operators.gated(order,
						vds -> addVerdict(vds.get(0), vds.get(1), vds.get(2), arithmetic, order, step),
						this::computedThird)
				.apply(watchedTerms(), state);
	}

	/**
	 * The functional dependency read at the free position, over the others'
	 * whole domains: the hull of {@code a + b = rhs}. Point operands make a
	 * point hull — the binding; wide ones mint the domain.
	 */
	private Verdict computedThird(Operators.SoleFree<Object> free) {
		Bound<Object> lo, hi;
		if (free.getPosition() == 2) {
			lo = Operators.plus(free.domainAt(0).lower(), free.domainAt(1).lower(), arithmetic);
			hi = Operators.plus(free.domainAt(0).upper(), free.domainAt(1).upper(), arithmetic);
		} else if (free.getPosition() == 1) {
			lo = minus(free.domainAt(2).lower(), free.domainAt(0).upper(), arithmetic);
			hi = minus(free.domainAt(2).upper(), free.domainAt(0).lower(), arithmetic);
		} else {
			lo = minus(free.domainAt(2).lower(), free.domainAt(1).upper(), arithmetic);
			hi = minus(free.domainAt(2).upper(), free.domainAt(1).lower(), arithmetic);
		}
		return Operators.mintHull(free.getVariable(), lo, hi, order, step);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new Add(terms, arithmetic, order, step);
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
				Operators.plus(uLo, vLo, arithmetic),
				Operators.plus(uUp, vUp, arithmetic),
				order, step);

		Interval<T> vi = Interval.of(
				minus(wLo, uUp, arithmetic),
				minus(wUp, uLo, arithmetic),
				order, step);

		Interval<T> ui = Interval.of(
				minus(wLo, vUp, arithmetic),
				minus(wUp, vLo, arithmetic),
				order, step);

		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.<VarWithDomain<?>> asList(
						VarWithDomain.of(w.getUnifiable(), wi),
						VarWithDomain.of(v.getUnifiable(), vi),
						VarWithDomain.of(u.getUnifiable(), ui))));
	}
}
