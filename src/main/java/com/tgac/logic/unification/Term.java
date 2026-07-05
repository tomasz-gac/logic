package com.tgac.logic.unification;

// ABOUTME: Structural root of all logic terms — values, input variables and reified variables.
// ABOUTME: Walk, unification and reification machinery operate at this level.

import io.vavr.control.Option;
import java.util.function.Supplier;

/**
 * A node in a logic term. Capability interfaces refine it:
 * {@link Unifiable} marks terms that may enter a solver, and reified
 * terms are what a solver emits.
 *
 * @author TGa
 */
public interface Term<T> extends Supplier<T> {

	default Option<T> asVal() {
		return Option.none();
	}

	default boolean isVal() {
		return false;
	}

	default Option<LVar<T>> asVar() {
		return Option.none();
	}

	default Option<ReifiedVar<T>> asReified() {
		return Option.none();
	}

	@Override
	default T get() {
		return ((LVal<T>) this).getValue();
	}

	default LVar<T> getVar() {
		return (LVar<T>) this;
	}
}
