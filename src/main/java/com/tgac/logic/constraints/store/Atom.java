package com.tgac.logic.constraints.store;

// ABOUTME: The singular case of a constraint: one named item with its watched
// ABOUTME: surface and payload — the unit families accumulate and split into.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Term;
import java.util.stream.Stream;

/**
 * One constraint item: the unit a family accumulates by meet and decomposes
 * into by split. {@code name} and {@code payload} are the marshal face —
 * a family's serialized form is its atoms through the registry; {@code
 * watched} is the variable surface boundary checks and wake filters scan.
 * The family algebra between an atom and its resident factor (meet, leq)
 * arrives with the singleton family views — an atom's class field names its
 * family until then.
 */
public interface Atom<F extends Factor<F>> extends PartialOrder<Atom<F>> {

	/**
	 * Single-atom entailment: this ⊑ other iff this atom alone implies that
	 * one — the covering order's fuel. Default is STRUCTURAL equality (grade
	 * one of the tower); sharp overrides (domain containment, nogood
	 * subsumption) are sound only over WALKED terms and must answer false
	 * over open ones.
	 */
	@Override
	default boolean leq(Atom<F> other) {
		return equals(other);
	}


	Class<? extends F> getFactorClass();

	String name();

	Stream<Term<?>> watched();

	Object payload();

	Fiber<Atom<F>> rename(Renaming renaming);
}
