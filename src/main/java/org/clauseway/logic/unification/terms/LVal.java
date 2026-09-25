package org.clauseway.logic.unification.terms;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import java.util.Optional;

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
	public Optional<T> asVal() {
		// empty for a null payload — a bound NULL is still a VALUE, so
		// bindness is isVal()/get(), never this face's presence
		return Optional.ofNullable(value);
	}

	@Override
	public boolean isVal() {
		return true;
	}
}
