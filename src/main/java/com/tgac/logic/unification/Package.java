package com.tgac.logic.unification;
import com.tgac.logic.ckanren.ConstraintStore;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {

	HashMap<LVar<?>, Unifiable<?>> substitutions;

	Store constraints;

	public static Package empty() {
		return new Package(HashMap.empty(), null);
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
	<T> Option<Unifiable<T>> get(LVar<T> v) {
		return substitutions.get(v).map(w -> (Unifiable<T>) w);
	}

	public long size() {
		return substitutions.size();
	}

	public Package withStore(ConstraintStore empty) {
		if (constraints != null) {
			if (empty.getClass().isInstance(constraints)) {
				return this;
			} else {
				throw new IllegalStateException("Constraint store already exists: " + constraints);
			}
		} else {
			return Package.of(substitutions, empty);
		}
	}
}
