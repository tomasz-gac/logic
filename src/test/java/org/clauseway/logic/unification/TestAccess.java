package org.clauseway.logic.unification;

import org.clauseway.logic.goals.Package;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;

public class TestAccess {
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
