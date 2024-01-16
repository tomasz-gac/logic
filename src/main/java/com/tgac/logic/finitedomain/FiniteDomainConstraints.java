package com.tgac.logic.finitedomain;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.ConstraintStore;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import static com.tgac.logic.ckanren.CKanren.runConstraints;
import static com.tgac.logic.ckanren.StoreSupport.getConstraintStore;

@Value
@RequiredArgsConstructor(staticName = "of")
class FiniteDomainConstraints implements ConstraintStore {
	private static final FiniteDomainConstraints EMPTY = new FiniteDomainConstraints(LinkedHashMap.empty(), HashSet.empty());

	public static Package register(Package p) {
		return p.withStore(EMPTY);
	}

	// cKanren domains
	LinkedHashMap<LVar<?>, Domain<?>> domains;

	// cKanren constraints
	HashSet<Constraint> constraints;

	public static FiniteDomainConstraints empty() {
		return EMPTY;
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return FiniteDomainConstraints.of(domains, constraints.remove((Constraint) c));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return FiniteDomainConstraints.of(domains, constraints.add((Constraint) c));
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof Constraint &&
				constraints.contains((Constraint) c);
	}

	public static FiniteDomainConstraints getFDStore(Package p) {
		return (FiniteDomainConstraints) getConstraintStore(p);
	}

	public static <T> Option<Domain<T>> getDom(Package p, LVar<T> x) {
		return getFDStore(p).getDomain(x);
	}

	@Override
	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return EnforceConstraintsFD.enforceConstraints(x);
	}

	@Override
	@SuppressWarnings("unchecked")
	public PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return s -> MiniKanren.prefixS(s.getSubstitutions(), newSubstitutions)
				.toJavaStream()
				.<PackageAccessor> map(ht -> ht
						.apply((x, v) -> FiniteDomainConstraints.getDom(s, x)
								.map(Domain.class::cast)
								.map(dom -> dom.processDom(v))
								.getOrElse(PackageAccessor.identity())
								.compose(runConstraints(x, constraints))))
				.reduce(PackageAccessor.identity(), PackageAccessor::compose)
				.apply(s.withSubstitutions(newSubstitutions));
	}

	@Override
	public <A> Unifiable<A> reify(Unifiable<A> unifiable, Package renameSubstitutions, Package p) {
		throw new IllegalStateException("Unbound variables");
	}

	public <T> Option<Domain<T>> getDomain(LVar<T> v) {
		return domains.get(v)
				.flatMap(Types.castAs(Domain.class));
	}

	public FiniteDomainConstraints withDomain(LVar<?> x, Domain<?> xd) {
		return FiniteDomainConstraints.of(domains.put(x, xd), constraints);
	}
}
