package com.tgac.logic.finitedomain;

// ABOUTME: The finite-domain store: a LatticeStore over Domain values whose capability
// ABOUTME: record is membership, singleton collapse and the equal-domain guard.

import com.tgac.functional.reflection.Types;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.LatticeFactor;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.stream.Collectors;

/**
 * The prototype {@link LatticeFactor} instance (docs/design/lattice-store.md):
 * component lattice {@link Domain} (meet = intersect), verification is
 * membership, a {@link Singleton} collapses to an inferred binding, and the
 * termination guard is exact domain equality — finite descent. Labelling
 * ({@link EnforceConstraintsFD}) is this store's {@code enforce}.
 */
class FiniteDomainConstraints extends LatticeFactor<Domain<Object>, FiniteDomainConstraints> {

	private static final FiniteDomainConstraints EMPTY =
			new FiniteDomainConstraints(Theory.empty());

	// the canonical dead store: any-empty-domain meets normalize to it, and the
	// cascade transitions to it on a failing update, so ⊥ IS the branch death
	private static final FiniteDomainConstraints BOTTOM =
			new FiniteDomainConstraints(Theory.empty());

	private FiniteDomainConstraints(Theory<FiniteDomainConstraints> theory) {
		super(theory);
	}

	public static Package register(Package p) {
		return Constraint.register(p, EMPTY);
	}

	public static FiniteDomainConstraints empty() {
		return EMPTY;
	}

	static FiniteDomainConstraints bottom() {
		return BOTTOM;
	}

	public static FiniteDomainConstraints getFDStore(Package p) {
		return Constraint.in(p, FiniteDomainConstraints.class)
				.map(Constraint::getFactor)
				.getOrElseThrow(() -> new IllegalStateException(
						"No store associated with package"));
	}

	public static <T> Option<Domain<T>> getDom(Package p, Term<T> x) {
		return getFDStore(p).getDomain(x);
	}

	/**
	 * The statement-position re-examination seam ({@code dom} narrowing an
	 * existing domain, labelling's catch-up): drains this store's own cascade
	 * from {@code x} against the live state.
	 */
	static Goal reexamine(Term<?> x) {
		return EMPTY.reexamineOwn(x);
	}

	// cKanren domains — keyed by NAME: a live LVar or a canonical Any
	public LinkedHashMap<Term<?>, Domain<?>> getDomains() {
		return LinkedHashMap.ofEntries(impositions()
				.map(i -> io.vavr.Tuple.of(i.getTarget(), (Domain<?>) i.getValue()))
				.collect(Collectors.toList()));
	}

	// cKanren constraints
	public HashSet<Propagator<FiniteDomainConstraints>> getConstraints() {
		return HashSet.ofAll(props().collect(Collectors.toList()));
	}

	public <T> Option<Domain<T>> getDomain(Term<T> v) {
		return getValue(v)
				.flatMap(Types.castAs(Domain.class));
	}

	@SuppressWarnings("unchecked")
	public FiniteDomainConstraints withDomain(Term<?> x, Domain<?> xd) {
		return withValue(x, (Domain<Object>) xd);
	}

	@Override
	protected FiniteDomainConstraints create(Theory<FiniteDomainConstraints> theory) {
		return new FiniteDomainConstraints(theory);
	}

	@Override
	protected FiniteDomainConstraints bottomStore() {
		return BOTTOM;
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return EnforceConstraintsFD.enforceConstraints(x);
	}
}
