package org.clauseway.logic.finitedomain.relations;

// ABOUTME: The strict-order schema: less < more as ONE atom — open bounds narrow
// ABOUTME: both ways immediately; doomed the moment a ground comparison violates.

import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.FiniteDomainConstraints;
import org.clauseway.logic.finitedomain.relations.Operators.VarWithDomain;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.lattice.Propagator;
import org.clauseway.logic.lattice.Verdict;
import org.clauseway.logic.unification.terms.Term;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.Comparator;

public final class Lss extends Propagator<FiniteDomainConstraints> {

	private final Comparator<Object> order;

	@SuppressWarnings("unchecked")
	public Lss(Term<?> less, Term<?> more, Comparator<?> order) {
		this(Array.of(less, more), (Comparator<Object>) order);
	}

	private Lss(Array<? extends Term<?>> terms, Comparator<Object> order) {
		super(terms);
		this.order = order;
	}

	@Override
	public Verdict propagate(Package state) {
		return Operators.gated(order,
						vds -> lssVerdict(vds.get(0), vds.get(1), order))
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
		return Operators.cmpOrder(state.substitution(),
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

	@SuppressWarnings("unchecked")
	static <T> Verdict lssVerdict(VarWithDomain<T> lss, VarWithDomain<T> mor, Comparator<T> order) {
		if (lss.getUnifiable().isVal() && mor.getUnifiable().isVal()) {
			// ground: the strict order decides exactly
			return order.compare(lss.getUnifiable().get(), mor.getUnifiable().get()) < 0 ?
					Verdict.subsumed() : Verdict.fail();
		}
		// the strict bound is the other side's bound with its point excluded
		Domain<T> lessDom = lss.<T> getDomain().atMost(mor.<T> getDomain().upper().opened());
		Domain<T> moreDom = mor.<T> getDomain().atLeast(lss.<T> getDomain().lower().opened());
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
