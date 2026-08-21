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
import io.vavr.Tuple;
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

	private static final FiniteDomainConstraints EMPTY = new FiniteDomainConstraints();

	private FiniteDomainConstraints() {
	}

	public static Package register(Package p) {
		return Constraint.register(p, EMPTY);
	}

	public static FiniteDomainConstraints empty() {
		return EMPTY;
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
	public static LinkedHashMap<Term<?>, Domain<?>> getDomains(Package p) {
		return Constraint.in(p, FiniteDomainConstraints.class)
				.map(pair -> LinkedHashMap.<Term<?>, Domain<?>> ofEntries(
						EMPTY.impositions(pair.getTheory())
								.map(i -> Tuple.<Term<?>, Domain<?>> of(i.getTarget(), i.getValue()))
								.collect(Collectors.toList())))
				.getOrElse(LinkedHashMap.empty());
	}

	// cKanren constraints
	public static HashSet<Propagator<FiniteDomainConstraints>> getConstraints(Package p) {
		return Constraint.in(p, FiniteDomainConstraints.class)
				.map(pair -> HashSet.ofAll(EMPTY.props(pair.getTheory())
						.collect(Collectors.toList())))
				.getOrElse(HashSet.empty());
	}

	/** Narrowing write on a theory: the domain fuses with any existing entry at {@code x}. */
	@SuppressWarnings("unchecked")
	public static Theory<FiniteDomainConstraints> withDomain(
			Theory<FiniteDomainConstraints> theory, Term<?> x, Domain<?> xd) {
		return EMPTY.withValue(theory, x, (Domain<Object>) xd);
	}

	public static <T> Option<Domain<T>> getDom(Package p, Term<T> x) {
		return Constraint.in(p, FiniteDomainConstraints.class)
				.flatMap(pair -> EMPTY.getValue(pair.getTheory(), x))
				.flatMap(Types.castAs(Domain.class));
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return EnforceConstraintsFD.enforceConstraints(x);
	}
}
