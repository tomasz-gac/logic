package com.tgac.logic.unification;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.MODULE)
public class LTree<T> {
	Unifiable<T> value;
	Unifiable<LList<LTree<T>>> children;

	public static <T> Unifiable<LTree<T>> of(Unifiable<T> value, Unifiable<LList<LTree<T>>> children) {
		return LVal.lval(new LTree<>(value, children));
	}
}
