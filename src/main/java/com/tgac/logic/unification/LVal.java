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
public class LVal<T> implements Unifiable<T>, Reified<T> {
	T value;

	/** A null payload is a VALUE — it equals itself and nothing else. */
	public static <T> Unifiable<T> lval(T v) {
		return new LVal<>(v);
	}

	@Override
	public String toString() {
		return "{" + value + '}';
	}

	@Override
	public Option<T> asVal() {
		// some, not of: a null payload is a bound value, never an absence
		return Option.some(value);
	}

	@Override
	public boolean isVal() {
		return true;
	}
}
