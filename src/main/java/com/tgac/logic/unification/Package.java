package com.tgac.logic.unification;

import com.tgac.logic.ckanren.ConstraintStore;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {

	HashMap<LVar<?>, Unifiable<?>> substitutions;

	LinkedHashMap<Class<? extends Store>, Store> constraints;

	public static Package empty() {
		return new Package(HashMap.empty(), LinkedHashMap.empty());
	}

	public Package extendS(HashMap<LVar<?>, Unifiable<?>> s) {
		return new Package(substitutions.merge(s), constraints);
	}

	public Package withSubstitutions(HashMap<LVar<?>, Unifiable<?>> s) {
		return new Package(s, constraints);
	}

	<T> Package put(LVar<T> key, Unifiable<T> value) {
		return Package.of(substitutions.put(key, value), constraints);
	}

	@SuppressWarnings("unchecked")
	<T> Unifiable<T> get(LVar<T> v) {
		return (Unifiable<T>) substitutions.getOrElse(v, null);
	}

	public <T> Unifiable<T> walk(Unifiable<T> v) {
		if (v.asVal().isDefined()) {
			return v;
		}
		if (get(v.getVar()) == null) {
			// it's important to return the same object
			// because we test with == to see if var is bound
			return v;
		}
		Unifiable<?> result = v;
		Unifiable<?> tmp;
		while ((tmp = get(result.getVar())) != null) {
			result = tmp;
			if (result.isVal()) {
				break;
			}
		}
		return (Unifiable<T>) result;
	}

	public long size() {
		return substitutions.size();
	}

	public Package withStore(ConstraintStore empty) {
		if (constraints.get(empty.getClass()).isDefined()) {
			return this;
		} else {
			return Package.of(substitutions, constraints.put(empty.getClass(), empty));
		}
	}
}
