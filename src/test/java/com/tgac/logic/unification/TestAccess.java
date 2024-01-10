package com.tgac.logic.unification;
import io.vavr.control.Option;
public class TestAccess {
	public static <T> LVar<T> lvarUnsafe() {
		return new LVar<>();
	}

	public static <T> Package put(Package s, LVar<T> key, Unifiable<T> value) {
		return Package.of(s.getSubstitutions().put(key, value), s.getSConstraints(), s.getConstraints());
	}

	@SuppressWarnings("unchecked")
	public static <T> Option<Unifiable<T>> get(Package s, LVar<T> v) {
		return s.getSubstitutions().get(v).map(w -> (Unifiable<T>) w);
	}
}
