package com.tgac.logic.finitedomain;

// ABOUTME: The strict-order schema: less < more as ONE atom — sharp bounds narrow
// ABOUTME: both ways immediately; doomed the moment a ground comparison violates.

import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;

final class LssO extends Propagator<FiniteDomainConstraints> {

	LssO(Term<?> less, Term<?> more) {
		this(Array.of(less, more));
	}

	private LssO(Array<? extends Term<?>> terms) {
		super(terms);
	}

	@Override
	public Verdict propagate(Package state) {
		return FiniteDomain.<Object> gated(vds ->
						Tuple.of(vds.get(0), vds.get(1)).apply(FiniteDomain::lssVerdict))
				.apply(watchedTerms(), state);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new LssO(terms);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public boolean doomed(Package state) {
		return FiniteDomain.cmpOrder(state.substitution(),
				watchedTerms().get(0), watchedTerms().get(1), c -> c < 0) == 0;
	}

	@Override
	public String name() {
		return "lss";
	}

	@Override
	public Class<? extends FiniteDomainConstraints> getFactorClass() {
		return FiniteDomainConstraints.class;
	}
}
