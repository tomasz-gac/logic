package com.tgac.logic.finitedomain.relations;

// ABOUTME: The leq schema: less ≤ more — bounds narrow both ways; doomed the
// ABOUTME: moment a ground comparison already violates the order.

import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.relations.Operators.VarWithDomain;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.Comparator;

public final class Leq extends Propagator<FiniteDomainConstraints> {

	private final Comparator<Object> order;

	@SuppressWarnings("unchecked")
	public Leq(Term<?> less, Term<?> more, Comparator<?> order) {
		this(Array.of(less, more), (Comparator<Object>) order);
	}

	private Leq(Array<? extends Term<?>> terms, Comparator<Object> order) {
		super(terms);
		this.order = order;
	}

	@Override
	public Verdict propagate(Package state) {
		return Operators.gated(order, vds -> leqVerdict(vds.get(0), vds.get(1), order))
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
		return Operators.cmpOrder(state.substitution(),
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

	@SuppressWarnings("unchecked")
	static <T> Verdict leqVerdict(VarWithDomain<T> lss, VarWithDomain<T> mor, Comparator<T> order) {
		if (lss.getUnifiable().isVal() && mor.getUnifiable().isVal()) {
			// ground: the order decides exactly, nothing left to watch
			return order.compare(lss.getUnifiable().get(), mor.getUnifiable().get()) <= 0 ?
					Verdict.subsumed() : Verdict.fail();
		}
		Domain<T> lessDom = lss.<T> getDomain().atMost(mor.<T> getDomain().upper());
		Domain<T> moreDom = mor.<T> getDomain().atLeast(lss.<T> getDomain().lower());
		if (lessDom.isEmpty() || moreDom.isEmpty()) {
			return Verdict.fail();
		}
		return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
				(Theory<FiniteDomainConstraints>) theory,
				Arrays.asList(
						VarWithDomain.of(lss.getUnifiable(), lessDom),
						VarWithDomain.of(mor.getUnifiable(), moreDom))));
	}
}
