package com.tgac.logic;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.Domain;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {
	// substitutions
	HashMap<LVar<?>, Unifiable<?>> substitutions;
	// separateness constraints
	List<HashMap<LVar<?>, Unifiable<?>>> sConstraints;
	// cKanren domains
	LinkedHashMap<LVar<?>, Domain> domains;
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

	public Option<Domain> getDomain(LVar<?> v) {
		return domains.get(v);
	}

	Package putSepConstraint(HashMap<LVar<?>, Unifiable<?>> constraint) {
		return Package.of(substitutions, sConstraints.prepend(constraint), domains, constraints);
	}

	public Package withoutConstraint(Constraint c) {
		return Package.of(substitutions, sConstraints, domains, constraints.remove(c));
	}

	public Package withoutSubstitutions() {
		return Package.of(HashMap.empty(), sConstraints, domains, constraints);
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
