package com.tgac.logic.fd;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.LVar;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.ConstraintStore;
import com.tgac.logic.cKanren.PackageAccessor;
import com.tgac.logic.fd.domains.FiniteDomain;
import com.tgac.logic.fd.parameters.EnforceConstraintsFD;
import com.tgac.logic.fd.parameters.ProcessPrefixFd;
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
	public static void use() {
		Package.register(FiniteDomainConstraints.empty());
	}

	// cKanren domains
	LinkedHashMap<LVar<?>, FiniteDomain<?>> domains;

	// cKanren constraints
	List<Constraint> constraints;

	public static FiniteDomainConstraints empty() {
		return EMPTY;
	}

	@Override
	public ConstraintStore remove(Constraint c) {
		return FiniteDomainConstraints.of(domains, constraints.remove(c));
	}

	@Override
	public ConstraintStore prepend(Constraint c) {
		return FiniteDomainConstraints.of(domains, constraints.prepend(c));
	}
	@Override
	public boolean contains(Constraint c) {
		return constraints.contains(c);
	}

	public static FiniteDomainConstraints getFDStore(Package p) {
		return p.get(FiniteDomainConstraints.class);
	}

	public static <T> Option<FiniteDomain<T>> getDom(Package p, LVar<T> x) {
		return getFDStore(p).getDomain(x);
	}

	@Override
	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return EnforceConstraintsFD.enforceConstraints(x);
	}
	@Override
	public PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> prefix) {
		return ProcessPrefixFd.processPrefix(prefix, constraints);
	}
	@Override
	public <A> Try<Unifiable<A>> reify(Unifiable<A> unifiable, Package renameSubstitutions, Package p) {
		return Try.failure(new IllegalStateException("Unbound variables"));
	}

	public <T> Option<FiniteDomain<T>> getDomain(LVar<T> v) {
		return domains.get(v)
				.flatMap(Types.castAs(FiniteDomain.class));
	}
	public FiniteDomainConstraints withDomain(LVar<?> x, FiniteDomain<?> xd) {
		return FiniteDomainConstraints.of(domains.put(x, xd), constraints);
	}
}
