package com.tgac.logic.finitedomain;

// ABOUTME: The mul schema: a * b = rhs — interval bounds narrow all three
// ABOUTME: positions; ground triples verify exactly.

import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;

final class MulO extends Propagator<FiniteDomainConstraints> {

	MulO(Term<?> a, Term<?> b, Term<?> rhs) {
		this(Array.of(a, b, rhs));
	}

	private MulO(Array<? extends Term<?>> terms) {
		super(terms);
	}

	@Override
	public Verdict propagate(Package state) {
		return FiniteDomain.<Object> gated(vds ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) -> FiniteDomain.mulVerdict(u, v, w,
										u.getDomain().min(), v.getDomain().min(), w.getDomain().min(),
										u.getDomain().max(), v.getDomain().max(), w.getDomain().max())))
				.apply(watchedTerms(), state);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new MulO(terms);
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
