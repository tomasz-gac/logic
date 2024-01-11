package com.tgac.logic.finitedomain;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.ckanren.RunnableConstraint;
import com.tgac.logic.ckanren.parameters.ConstraintStore;
import com.tgac.logic.finitedomain.domains.FiniteDomain;
import com.tgac.logic.finitedomain.parameters.EnforceConstraintsFD;
import com.tgac.logic.finitedomain.parameters.ProcessPrefixFd;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class FiniteDomainConstraints implements ConstraintStore {
	private static final FiniteDomainConstraints EMPTY = new FiniteDomainConstraints(LinkedHashMap.empty(), List.empty());

	public static Package register(Package p) {
		return p.withConstraintStore(EMPTY);
	}

	// cKanren domains
	LinkedHashMap<LVar<?>, FiniteDomain<?>> domains;

	// cKanren constraints
	List<RunnableConstraint> constraints;

	public static FiniteDomainConstraints empty() {
		return EMPTY;
	}

	@Override
	public ConstraintStore remove(Constraint c) {
		return FiniteDomainConstraints.of(domains, constraints.remove((RunnableConstraint) c));
	}

	@Override
	public ConstraintStore prepend(Constraint c) {
		return FiniteDomainConstraints.of(domains, constraints.prepend((RunnableConstraint) c));
	}

	@Override
	public boolean contains(Constraint c) {
		return c instanceof RunnableConstraint &&
				constraints.contains((RunnableConstraint) c);
	}

	public static FiniteDomainConstraints getFDStore(Package p) {
		return (FiniteDomainConstraints) p.getConstraintStore();
	}

	public static <T> Option<FiniteDomain<T>> getDom(Package p, LVar<T> x) {
		return getFDStore(p).getDomain(x);
	}

	@Override
	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return EnforceConstraintsFD.enforceConstraints(x);
	}
	@Override
	public PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return ProcessPrefixFd.processPrefix(newSubstitutions, constraints);
	}
	@Override
	public <A> Try<Unifiable<A>> reify(Unifiable<A> unifiable, Package renameSubstitutions, Package p) {
		return unifiable.asVar()
				.filter(v -> getDomain(v).isDefined() ||
						constraints.toJavaStream()
								.anyMatch(r -> r.getArgs().contains(v)))
				.map(v -> Try.<Unifiable<A>> failure(new IllegalStateException("Unbound variables")))
				.getOrElse(() -> Try.success(unifiable));
	}

	public <T> Option<FiniteDomain<T>> getDomain(LVar<T> v) {
		return domains.get(v)
				.flatMap(Types.castAs(FiniteDomain.class));
	}

	public FiniteDomainConstraints withDomain(LVar<?> x, FiniteDomain<?> xd) {
		return FiniteDomainConstraints.of(domains.put(x, xd), constraints);
	}
}
