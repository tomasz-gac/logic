package com.tgac.logic.finitedomain;

// ABOUTME: The leq schema: less ≤ more — bounds narrow both ways; doomed the
// ABOUTME: moment a ground comparison already violates the order.

import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Comparator;

final class Leq extends Propagator<FiniteDomainConstraints> {

	private final Comparator<Object> order;

	@SuppressWarnings("unchecked")
	Leq(Term<?> less, Term<?> more, Comparator<?> order) {
		this(Array.of(less, more), (Comparator<Object>) order);
	}

	private Leq(Array<? extends Term<?>> terms, Comparator<Object> order) {
		super(terms);
		this.order = order;
	}

	@Override
	public Verdict propagate(Package state) {
		return FiniteDomain.gated(order, Option.<Discrete<Object>> none(),
						(Array<FiniteDomain.VarWithDomain<Object>> vds) ->
								Tuple.of(vds.get(0), vds.get(1))
										.apply((l, m) -> FiniteDomain.leqVerdict(l, m, order)))
				.apply(watchedTerms(), state);
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new Leq(terms, order);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public boolean doomed(Package state) {
		return FiniteDomain.cmpOrder(state.substitution(),
				watchedTerms().get(0), watchedTerms().get(1), c -> c <= 0, order) == 0;
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
