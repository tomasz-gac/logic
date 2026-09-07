package com.tgac.logic.tabling;

// ABOUTME: The cache key of a tabled call: relation identity, reified arguments,
// ABOUTME: and per-store residues — the call's REGION, not just its pattern.

import com.tgac.logic.tabling.subsumption.Subsumption;
import com.tgac.logic.unification.Reified;
import lombok.Value;

/**
 * A call to a relation with specific arguments under specific constraint
 * knowledge.
 *
 * The relation slot is generic: {@code R} is any identity token naming the
 * relation — a goal's {@link Tabled}, or any other canonical identity a
 * caller keys its tables by. Two calls are equal when they apply the same
 * relation (equal tokens name one relation) to alpha-equivalent arguments — reification makes
 * plain equality decide variance — under EQUAL residues: each projecting
 * store's knowledge about the call's free vars (positional, slot i = the
 * i-th any in first-occurrence order), keyed by store class. A
 * constraint-free call has no residues, so pre-TCLP keys are unchanged.
 */
@Value
public class Call<R> {
	R relation;
	Reified<?> arguments;
	Residues residues;

	public static <R> Call<R> of(R relation, Reified<?> arguments) {
		return new Call<>(relation, arguments, Residues.TRUE);
	}

	public static <R> Call<R> of(R relation, Reified<?> arguments, Residues residues) {
		return new Call<>(relation, arguments, residues);
	}

	/**
	 * Region containment: does this call's region cover {@code other}'s?
	 * Same relation by equality, arguments by {@link Subsumption#subsumes},
	 * residues by pointwise entailment — {@code other ⊑ this} per store
	 * (absent = ⊤; a class this call knows about that other does not is a
	 * refusal: a narrower entry never serves a wider caller). Carried
	 * couplings compare by store-object identity — recursion under one
	 * constraint context shares (the same propagator persists down the
	 * store); independent same-shaped posts are conservatively incomparable.
	 * When containment holds, every answer {@code other} is entitled to is
	 * among this call's answers (the subset property), so this call's entry —
	 * open or sealed — may serve {@code other} through consume's filter.
	 */
	public boolean subsumes(Call<?> other) {
		return relation.equals(other.relation)
				&& Subsumption.subsumes(arguments, other.arguments)
				&& other.residues.leq(residues);
	}

	@Override
	public String toString() {
		return relation + "" + arguments + (residues.isTrue() ? "" : residues.toString());
	}
}
