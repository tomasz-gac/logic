package com.tgac.logic.unification;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.MODULE)
public class LTree<T> {
	private static Unifiable<?> EMPTY = LVal.lval(new LTree<>(null, null));
	Term<T> value;
	Term<LList<LTree<T>>> children;

	public static <T> Unifiable<LTree<T>> of(Term<T> value) {
		return LVal.lval(new LTree<>(value, LList.empty()));
	}

	public static <T> Unifiable<LTree<T>> of(Term<T> value, Term<LList<LTree<T>>> children) {
		return LVal.lval(new LTree<>(value, children));
	}

	@SafeVarargs
	public static <T> Unifiable<LTree<T>> ofAll(T value, LTree<T>... children) {
		return LVal.lval(new LTree<>(LVal.lval(value), LList.ofAll(children)));
	}

	public static <T> Unifiable<LTree<T>> empty() {
		return (Unifiable<LTree<T>>) EMPTY;
	}

	public boolean isEmpty() {
		return this == EMPTY.get();
	}
}
