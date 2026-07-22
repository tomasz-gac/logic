package com.tgac.logic.constraints.store;

// ABOUTME: A store that can report its knowledge about a var list as a residue
// ABOUTME: and re-impose one — the projection half of tabled constraints.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import java.util.List;

/**
 * The projection capability: report this store's knowledge about a POSITIONAL
 * var list as an opaque residue, and re-impose a residue onto live vars. The
 * residue's only cross-store obligation is its {@link PartialOrder}:
 * {@code mine.leq(theirs)} is entailment, which is all consumers ever ask
 * (subsumption keys, cache reuse, dedup) — combination happens through
 * {@link Residue#restate} and the kernel's own machinery, never by merging
 * residue objects.
 *
 * <p>ONE VOCABULARY, EVERY USAGE: whatever {@code project} can express rides
 * every consumer of this capability alike — call keys and answer residues are
 * the same projection; there is no per-usage variant, and the store never
 * learns which side it is serving. What it cannot express it must FLAG
 * rather than silently drop (see {@code project}).
 *
 * <p>THE CORRESPONDENCE IS THE CALLER'S: slot {@code i} means {@code vars[i]},
 * and the caller owns the ordering discipline that makes residues align across
 * calls (tabling derives it from the reified key's holes in first-occurrence
 * order — no renaming machinery crosses this boundary). Callers pass WALKED
 * vars; the store reads its own knowledge, it does not chase substitutions.
 *
 *
 * <p>Participation in tabling requires this capability — knowledge that
 * cannot be projected cannot enter call keys, and unkeyed knowledge means
 * silently wrong cache reuse. TERMINATION is a separate, undeclared concern:
 * a store whose residues over a fixed var list form a finite lattice bounds
 * the tabling ascent (FD does, by construction); one that does not (record
 * sets over unbounded values) can ascend forever on adversarial programs —
 * the author's responsibility, exactly like tabling an unbounded generator.
 */
public interface Projectable<R extends Residue<R>> extends ConstraintStore {

	/**
	 * This store's knowledge about {@code vars}, slot i ↔ vars[i]; absence = ⊤.
	 * Projecting the EMPTY list is the ⊤ residue — the caller's triviality test.
	 *
	 * <p>THE CONTRACT: the whole truth about {@code vars}, or an HONEST flag —
	 * never a silently partial residue. Live knowledge about a supplied var
	 * that the residue cannot express (a coupling outside the vocabulary, or
	 * one escaping to an unsupplied local) sets {@link Residue#isWidened} —
	 * only the store can see the shortfall; only the BOUNDARY has the context
	 * to refuse, and its reasons differ by side (tabled-constraints.md §5.1):
	 * a widened ANSWER residue replays wrong answers (nothing re-filters) —
	 * refusal there is necessary; a widened CALL key is sound by containment
	 * (the master searches wider, the caller filters) — acceptance there IS
	 * the call-abstraction knob.
	 */
	R project(List<LVar<?>> vars);

	/**
	 * No LIVE knowledge remains under {@code state} — everything this store
	 * holds is spent bookkeeping (stale domains under bindings, discharged
	 * watchers). The answer-side gate while answers must come out ground:
	 * a tabled answer is admissible while its stores are discharged; live
	 * knowledge on an answer is refused until constrained answers exist
	 * (tabled-constraints.md stage 2, where this demotes to the ground-answer
	 * fast path — skip projecting when everything is spent). Conservative
	 * default: empty is discharged.
	 */
	default boolean discharged(Package state) {
		return isEmpty();
	}
}
