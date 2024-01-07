package com.tgac.logic;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.fd.domains.FiniteDomain;
import io.vavr.Predicates;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import static com.tgac.logic.MiniKanren.walk;
@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {
	// substitutions
	HashMap<LVar<?>, Unifiable<?>> substitutions;
	// separateness constraints
	List<HashMap<LVar<?>, Unifiable<?>>> sConstraints;
	// cKanren domains
	LinkedHashMap<LVar<?>, FiniteDomain<?>> domains;
	// cKanren constraints
	List<Constraint> constraints;

	public static Package empty() {
		return new Package(HashMap.empty(), List.empty(), LinkedHashMap.empty(), List.empty());
	}

	public Package extendS(HashMap<LVar<?>, Unifiable<?>> s) {
		return new Package(substitutions.merge(s), sConstraints, domains, constraints);
	}

	<T> Package put(LVar<T> key, Unifiable<T> value) {
		return Package.of(substitutions.put(key, value), sConstraints, domains, constraints);
	}

	@SuppressWarnings("unchecked")
	<T> Option<Unifiable<T>> get(LVar<T> v) {
		return substitutions.get(v).map(w -> (Unifiable<T>) w);
	}

	public <T> Option<FiniteDomain<T>> getDomain(LVar<T> v) {
		return domains.get(v)
				.flatMap(Types.castAs(FiniteDomain.class));
	}

	Package putSepConstraint(HashMap<LVar<?>, Unifiable<?>> constraint) {
		return Package.of(substitutions, sConstraints.prepend(constraint), domains, constraints);
	}

	public Package withoutConstraint(Constraint c) {
		return Package.of(substitutions, sConstraints, domains, constraints.remove(c));
	}

	/**
	 * Checks whether any item within v is unbound within r Original name: anyVar
	 */
	public Boolean isAssociated(Unifiable<?> v) {
		return v.asVar()
				.map(lvar -> walk(this, lvar) != lvar)
				.getOrElse(true);
	}

	public Package withConstraint(Constraint c) {
		boolean atLeaseOneIsBound = c.getArgs().toJavaStream()
				.anyMatch(Predicates.not(this::isAssociated));

		return atLeaseOneIsBound ?
				Package.of(substitutions, sConstraints, domains, constraints.prepend(c)) :
				this;
	}

	public Package withSubstitutionsFrom(Package aPackage) {
		return Package.of(aPackage.getSubstitutions(), sConstraints, domains, constraints);
	}

	public long size() {
		return substitutions.size();
	}

	public Package withoutConstraints() {
		return Package.of(substitutions, List.empty(), domains, constraints);
	}

}
