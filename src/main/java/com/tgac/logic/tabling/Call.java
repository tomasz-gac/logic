package com.tgac.logic.tabling;

// ABOUTME: The cache key of a tabled call: relation identity, reified arguments,
// ABOUTME: and per-store residues — the call's REGION, not just its pattern.

import com.tgac.logic.tabling.subsumption.Subsumption;
import com.tgac.logic.unification.Reified;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import lombok.Value;

/**
 * A call to a tabled relation with specific arguments under specific
 * constraint knowledge.
 *
 * Two calls are equal when they apply the same relation (by identity) to
 * alpha-equivalent arguments — reification makes plain equality decide
 * variance — under EQUAL residues: each projecting store's knowledge about
 * the call's free vars (positional, slot i = the i-th hole in first-occurrence
 * order), keyed by store class. A constraint-free call has no residues, so
 * pre-TCLP keys are unchanged.
 */
@Value
public class Call {
	Tabled<?> relation;
	Reified<?> arguments;
	Map<Class<?>, Object> residues;

	public static Call of(Tabled<?> relation, Reified<?> arguments) {
		return new Call(relation, arguments, HashMap.empty());
	}

	public static Call of(Tabled<?> relation, Reified<?> arguments, Map<Class<?>, Object> residues) {
		return new Call(relation, arguments, residues);
	}

	/**
	 * Herbrand call subsumption: does this pattern generalize {@code other}?
	 * Same relation by identity, arguments by {@link Subsumption#subsumes} —
	 * and CONSTRAINT-FREE ONLY: positional residues do not align across
	 * different hole counts, so region containment between constrained calls
	 * is stage-3 machinery. When it holds, every answer of {@code other} is
	 * among this call's answers (the subset property), so a SEALED entry for
	 * this call may serve {@code other} as a read-only relation.
	 */
	public boolean subsumes(Call other) {
		return relation == other.relation
				&& residues.isEmpty()
				&& other.residues.isEmpty()
				&& Subsumption.subsumes(arguments, other.arguments);
	}

	@Override
	public String toString() {
		return relation + "" + arguments + (residues.isEmpty() ? "" : residues.toString());
	}
}
