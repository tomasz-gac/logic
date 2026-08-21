package com.tgac.logic.constraints.store;

// ABOUTME: The package's constraint entry: a theory paired with its interpreter —
// ABOUTME: knowledge outside the factor, behavior and memo beside it.

import com.tgac.logic.goals.Packaged;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * One family's entry in the package: the {@link Theory} is the knowledge —
 * the lattice citizen, the thing crossings rename and keys carry — and the
 * {@link Factor} is the family's execution behavior plus its private memo.
 * Identity is the Theory half ALONE: the factor's state is reconstructible
 * from the theory by invariant (droppability — marshal never carries it),
 * so two entries with one theory are one constraint regardless of their
 * interpreters' private state.
 */
@Value
@EqualsAndHashCode(of = "theory")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraint<S extends Factor<S>> implements Packaged {
	Theory<S> theory;
	S factor;

	// hand-written: lombok's staticName cannot carry the recursive bound
	public static <S extends Factor<S>> Constraint<S> of(Theory<S> theory, S factor) {
		return new Constraint<>(theory, factor);
	}

	@Override
	public String toString() {
		return theory.toString();
	}
}
