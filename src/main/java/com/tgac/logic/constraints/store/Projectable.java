package com.tgac.logic.constraints.store;

// ABOUTME: A store that can report its knowledge about a var list as a residue
// ABOUTME: and re-impose one — the projection half of tabled constraints.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Unifiable;
import java.util.List;

/**
 * The projection capability: report this store's knowledge about a POSITIONAL
 * var list as an opaque residue, and re-impose a residue onto live vars. The
 * residue's only cross-store obligation is its {@link PartialOrder}:
 * {@code mine.leq(theirs)} is entailment, which is all consumers ever ask
 * (subsumption keys, cache reuse, dedup) — combination happens through
 * {@link #restate} and the kernel's own machinery, never on residues.
 *
 * <p>THE CORRESPONDENCE IS THE CALLER'S: slot {@code i} means {@code vars[i]},
 * and the caller owns the ordering discipline that makes residues align across
 * calls (tabling derives it from the reified key's holes in first-occurrence
 * order — no renaming machinery crosses this boundary). Callers pass WALKED
 * vars; the store reads its own knowledge, it does not chase substitutions.
 *
 * <p>{@code restate} must route through the store's PUBLIC statement entries
 * (constraint posts), so a replayed residue is propagated like any freshly
 * stated knowledge.
 *
 * <p>Participation in tabling requires this capability — knowledge that
 * cannot be projected cannot enter call keys, and unkeyed knowledge means
 * silently wrong cache reuse. TERMINATION is a separate, undeclared concern:
 * a store whose residues over a fixed var list form a finite lattice bounds
 * the tabling ascent (FD does, by construction); one that does not (record
 * sets over unbounded values) can ascend forever on adversarial programs —
 * the author's responsibility, exactly like tabling an unbounded generator.
 */
public interface Projectable<R extends PartialOrder<R>> extends ConstraintStore {

	/**
	 * This store's knowledge about {@code vars}, slot i ↔ vars[i]; absence = ⊤.
	 * Projecting the EMPTY list is the ⊤ residue — the caller's triviality test.
	 */
	R project(List<LVar<?>> vars);

	/** Re-impose {@code residue} onto live vars, same slots, via public posts. */
	Goal restate(R residue, List<Unifiable<?>> vars);

	/**
	 * No LIVE knowledge remains under {@code state} — everything this store
	 * holds is spent bookkeeping (stale domains under bindings, discharged
	 * watchers). The answer-side gate: a tabled answer is admissible while its
	 * stores are discharged; live knowledge on an answer is refused until
	 * constrained answers exist. Conservative default: empty is discharged.
	 */
	default boolean discharged(Package state) {
		return isEmpty();
	}
}
