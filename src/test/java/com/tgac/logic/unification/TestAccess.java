package com.tgac.logic.unification;

import com.tgac.logic.goals.Package;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;

public class TestAccess {
	public static <T> LVar<T> lvarUnsafe() {
		return new LVar<>();
	}

	public static <T> Package put(Package s, LVar<T> key, Unifiable<T> value) {
		return s.withSubstitutions(s.substitution().extend(key, value));
	}

	@SuppressWarnings("unchecked")
	public static <T> Option<Unifiable<T>> get(Package s, LVar<T> v) {
		return io.vavr.control.Option.of(s.substitution().binding(v)).map(w -> (Unifiable<T>) w);
	}

	/** White-box prefix mint — production code gets prefixes only from unification. */
	public static Prefix prefix(HashMap<LVar<?>, Term<?>> delta) {
		return new Prefix(delta);
	}
}
