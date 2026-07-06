package com.tgac.logic.tabling;

// ABOUTME: The cache key of a tabled call: the relation's identity plus its reified arguments.
// ABOUTME: Identity keying means relations sharing a display name never share a cache.

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

	@Override
	public String toString() {
		return relation.getName() + arguments;
	}
}
