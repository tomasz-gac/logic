package com.tgac.logic.unification;

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {

	HashMap<LVar<?>, Term<?>> substitutions;

	LinkedHashMap<Class<? extends Store>, Store> constraints;

	public static Package empty() {
		return new Package(HashMap.empty(), LinkedHashMap.empty());
	}

	public Package withSubstitutions(HashMap<LVar<?>, Term<?>> s) {
		return new Package(s, constraints);
	}

	<T> Package put(LVar<T> key, Term<T> value) {
		return Package.of(substitutions.put(key, value), constraints);
	}

	@SuppressWarnings("unchecked")
	<T> Term<T> get(LVar<T> v) {
		return (Term<T>) substitutions.getOrElse(v, null);
	}

	public <T> Term<T> walk(Term<T> v) {
		// only input vars are substitution keys; vals and reified vars walk to themselves
		if (!v.asVar().isDefined()) {
			return v;
		}
		if (get(v.getVar()) == null) {
			// it's important to return the same object
			// because we test with == to see if var is bound
			return v;
		}
		Term<?> result = v;
		Term<?> tmp;
		while (result.asVar().isDefined() && (tmp = get(result.getVar())) != null) {
			result = tmp;
		}
		return (Term<T>) result;
	}

	public long size() {
		return substitutions.size();
	}

	public Package withStore(Store empty) {
		if (constraints.get(empty.getClass()).isDefined()) {
			return this;
		} else {
			return Package.of(substitutions, constraints.put(empty.getClass(), empty));
		}
	}

	public Package putStore(Store store) {
		return Package.of(substitutions, constraints.put(store.getClass(), store));
	}

	public Package withoutStore(Class<? extends Store> cls) {
		return Package.of(substitutions, constraints.remove(cls));
	}
}
