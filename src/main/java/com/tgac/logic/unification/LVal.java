package com.tgac.logic.unification;

import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * @author TGa
 */

@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class LVal<T> implements Unifiable<T> {
	T value;

	public static <T> Unifiable<T> lval(@NonNull T v) {
		return new LVal<>(v);
	}

	@Override
	public String toString() {
		return "{" + value + '}';
	}

	@Override
	public Option<T> asVal() {
		return Option.of(value);
	}

	@Override
	public boolean isVal() {
		return true;
	}
}
