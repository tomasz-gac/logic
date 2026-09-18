package com.tgac.logic.finitedomain.relations;

// ABOUTME: The add schema: a + b = rhs — interval bounds narrow all three
// ABOUTME: positions; ground triples verify exactly.

import static com.tgac.logic.finitedomain.relations.Operators.minus;
import static com.tgac.logic.finitedomain.relations.Operators.widened;

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
import io.vavr.Tuple;
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
		return Operators.gated(order, (Array<VarWithDomain<Object>> vds) ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) -> addVerdict(u, v, w,
										arithmetic, order, step)))
				.apply(watchedTerms(), state);
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
				widened(Operators.plus(uUp, vUp, arithmetic), step),
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
}
