package com.tgac.logic.finitedomain;

// ABOUTME: The leq schema: less ≤ more — bounds narrow both ways; doomed the
// ABOUTME: moment a ground comparison already violates the order.

import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;

final class LeqO extends Propagator<FiniteDomainConstraints> {

	LeqO(Term<?> less, Term<?> more) {
		this(Array.of(less, more));
	}

	private LeqO(Array<? extends Term<?>> terms) {
		super(terms);
	}

	@Override
	public Verdict propagate(Package state) {
		return FiniteDomain.<Object> gated(vds ->
						Tuple.of(vds.get(0), vds.get(1)).apply(FiniteDomain::leqVerdict))
				.apply(watchedTerms(), state);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new LeqO(terms);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public boolean doomed(Package state) {
		return FiniteDomain.cmpOrder(state.substitution(),
				watchedTerms().get(0), watchedTerms().get(1), c -> c <= 0) == 0;
	}

	@Override
	public String name() {
		return "leq";
	}

	@Override
	public Class<? extends FiniteDomainConstraints> getFactorClass() {
		return FiniteDomainConstraints.class;
	}
}
