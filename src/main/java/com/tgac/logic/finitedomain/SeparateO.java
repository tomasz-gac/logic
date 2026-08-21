package com.tgac.logic.finitedomain;

// ABOUTME: The separate schema: l ≠ r — singleton collapse prunes the other
// ABOUTME: side; doomed the moment both sides walk to the same ground value.

import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.FiniteDomain.VarWithDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Collections;
import java.util.Objects;

final class SeparateO extends Propagator<FiniteDomainConstraints> {

	SeparateO(Term<?> l, Term<?> r) {
		this(Array.of(l, r));
	}

	private SeparateO(Array<? extends Term<?>> terms) {
		super(terms);
	}

	@Override
	public Verdict propagate(Package state) {
		return FiniteDomain.letDomain(state, FiniteDomain.<Object> typed(watchedTerms()))
				.map(ds -> Tuple.of(ds.get(0), ds.get(1)))
				.map(ds -> ds.apply(SeparateO::verdict))
				.getOrElse(Verdict::keep);
	}

	@SuppressWarnings("unchecked")
	private static <T> Verdict verdict(VarWithDomain<T> ld, VarWithDomain<T> rd) {
		Option<Tuple2<Arithmetic<T>, Arithmetic<T>>> zip = MiniKanren.zip(
				FiniteDomain.getSingleElement(ld.getDomain()),
				FiniteDomain.getSingleElement(rd.getDomain()));
		if (zip.isDefined() && zip.get().apply(Objects::equals)) {
			return Verdict.fail();
		}
		if (ld.getDomain().isDisjoint(rd.getDomain())) {
			return Verdict.subsumed();
		}
		if (ld.getDomain() instanceof Singleton) {
			return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
					(Theory<FiniteDomainConstraints>) theory,
					Collections.<VarWithDomain<?>> singletonList(VarWithDomain.of(
							rd.getUnifiable(),
							rd.<T> getDomain().difference(ld.getDomain())))));
		}
		if (rd.getDomain() instanceof Singleton) {
			return Verdict.update((state, theory) -> DomainUpdate.narrowAll(state,
					(Theory<FiniteDomainConstraints>) theory,
					Collections.<VarWithDomain<?>> singletonList(VarWithDomain.of(
							ld.getUnifiable(),
							ld.<T> getDomain().difference(rd.getDomain())))));
		}
		return Verdict.keep();
	}

	@Override
	public Propagator<FiniteDomainConstraints> watching(Array<? extends Term<?>> terms) {
		return new SeparateO(terms);
	}

	@Override
	public FiniteDomainConstraints empty() {
		return FiniteDomainConstraints.empty();
	}

	@Override
	public boolean doomed(Package state) {
		Term<?> lw = state.substitution().walk(watchedTerms().get(0));
		Term<?> rw = state.substitution().walk(watchedTerms().get(1));
		return lw.asVal().isDefined() && rw.asVal().isDefined() && lw.get().equals(rw.get());
	}

	@Override
	public String name() {
		return "separate";
	}

	@Override
	public Class<? extends FiniteDomainConstraints> getFactorClass() {
		return FiniteDomainConstraints.class;
	}
}
