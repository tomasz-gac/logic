package com.tgac.logic.finitedomain;

import static com.tgac.logic.ckanren.CKanren.runConstraints;
import static com.tgac.logic.ckanren.StoreSupport.getConstraintStore;

import com.tgac.functional.reflection.Types;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.ConstraintStore;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Predicates;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;

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
		return (FiniteDomainConstraints) getConstraintStore(p, FiniteDomainConstraints.class);
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
		Set<LVar<?>> varsWithDomains = domains.keySet().toJavaStream()
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.collect(Collectors.toSet());

		Set<LVar<?>> constrainedVarsWithoutDomains = constraints.toJavaStream()
				.map(Constraint::getArgs)
				.flatMap(List::stream)
				.map(p::walk)
				.flatMap(u -> u.asVar().toJavaStream())
				.filter(Predicates.not(varsWithDomains::contains))
				.collect(Collectors.toSet());

		if (!constrainedVarsWithoutDomains.isEmpty()) {
			throw new IllegalStateException("Variables without domain detected: " + constrainedVarsWithoutDomains);
		} else {
			return unifiable;
		}
	}

	public <T> Option<Domain<T>> getDomain(LVar<T> v) {
		return domains.get(v)
				.flatMap(Types.castAs(Domain.class));
	}

	public FiniteDomainConstraints withDomain(LVar<?> x, Domain<?> xd) {
		return FiniteDomainConstraints.of(domains.put(x, xd), constraints);
	}

	public <A> boolean constrains(Unifiable<A> u) {
		return domains.toJavaStream()
				.map(Tuple2::_1)
				.anyMatch(u::equals) ||
				constraints.toJavaStream()
						.map(Constraint::getArgs)
						.anyMatch(u::equals);
	}
}
