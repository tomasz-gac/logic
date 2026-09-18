package com.tgac.logic.finitedomain;

// ABOUTME: The mul schema: a * b = rhs — interval bounds narrow all three
// ABOUTME: positions; ground triples verify exactly.

import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Comparator;

final class Mul extends Propagator<FiniteDomainConstraints> {

	private final Multiplicative<Object> multiplicative;
	private final Comparator<Object> order;
	private final Option<Discrete<Object>> step;

	@SuppressWarnings("unchecked")
	Mul(Term<?> a, Term<?> b, Term<?> rhs,
			Multiplicative<?> multiplicative, Comparator<?> order, Option<? extends Discrete<?>> step) {
		this(Array.of(a, b, rhs),
				(Multiplicative<Object>) multiplicative,
				(Comparator<Object>) order,
				(Option<Discrete<Object>>) (Option<?>) step);
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
		return FiniteDomain.gated(order, (Array<FiniteDomain.VarWithDomain<Object>> vds) ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) -> FiniteDomain.mulVerdict(u, v, w,
										multiplicative, order, step)))
				.apply(watchedTerms(), state);
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
}
