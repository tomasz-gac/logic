package com.tgac.logic.finitedomain;

// ABOUTME: The strict-order schema: less < more as ONE atom — open bounds narrow
// ABOUTME: both ways immediately; doomed the moment a ground comparison violates.

import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import java.util.Comparator;

final class Lss extends Propagator<FiniteDomainConstraints> {

	private final Comparator<Object> order;

	@SuppressWarnings("unchecked")
	Lss(Term<?> less, Term<?> more, Comparator<?> order) {
		this(Array.of(less, more), (Comparator<Object>) order);
	}

	private Lss(Array<? extends Term<?>> terms, Comparator<Object> order) {
		super(terms);
		this.order = order;
	}

	@Override
	public Verdict propagate(Package state) {
		return FiniteDomain.gated(order,
						(Array<FiniteDomain.VarWithDomain<Object>> vds) ->
								Tuple.of(vds.get(0), vds.get(1))
										.apply((l, m) -> FiniteDomain.lssVerdict(l, m, order)))
				.apply(watchedTerms(), state);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new Lss(terms, order);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public boolean doomed(Package state) {
		return FiniteDomain.cmpOrder(state.substitution(),
				watchedTerms().get(0), watchedTerms().get(1), c -> c < 0, order) == 0;
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
