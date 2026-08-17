package com.tgac.logic.unification;

// ABOUTME: The any-value position in a reified answer: the output counterpart of LVar.
// ABOUTME: Equal by number — reification numbers anys canonically, so equality is alpha-equivalence.

import io.vavr.control.Option;
import lombok.Value;

/**
 * A variable position in a reified answer. Where an {@link LVar} is a
 * binding site identified by object identity, a reified var is a canonical
 * token identified by its NUMBER — reification assigns them by first
 * occurrence, so two answers sharing the shape share the numbers, which is
 * what makes plain equality on reified terms mean alpha-equivalence. The
 * canonical rendering is {@code _.number}.
 *
 * @author TGa
 */
@Value(staticConstructor = "of")
public class Any<T> implements Reified<T>, Name<T> {
	int number;

	@Override
	public Option<Name<T>> asName() {
		return Option.of(this);
	}

	@Override
	public Option<Any<T>> asReified() {
		return Option.of(this);
	}

	@Override
	public String toString() {
		return "_." + number;
	}
}
