package com.tgac.logic.tabling;

// ABOUTME: The cache key of a tabled call: the relation's identity plus its reified arguments.
// ABOUTME: Identity keying means distinct relations never share a cache.

import com.tgac.logic.tabling.subsumption.Subsumption;
import com.tgac.logic.unification.Reified;
import lombok.Value;

/**
 * A call to a tabled relation with specific arguments.
 *
 * Two calls are equal when they apply the same relation (by identity) to
 * alpha-equivalent arguments — the argument tuple is reified, so plain
 * equality on it decides variance.
 */
@Value
public class Call {
	Tabled<?> relation;
	Reified<?> arguments;

	public static Call of(Tabled<?> relation, Reified<?> arguments) {
		return new Call(relation, arguments);
	}

	/**
	 * Herbrand call subsumption: does this pattern generalize {@code other}?
	 * Same relation by identity, arguments by {@link Subsumption#subsumes}.
	 * When it holds, every answer of {@code other} is among this call's
	 * answers (the subset property), so a SEALED entry for this call may
	 * serve {@code other} as a read-only relation.
	 */
	public boolean subsumes(Call other) {
		return relation == other.relation
				&& Subsumption.subsumes(arguments, other.arguments);
	}

	@Override
	public String toString() {
		return relation + "" + arguments;
	}
}
