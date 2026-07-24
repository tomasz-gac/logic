package com.tgac.logic.constraints.store;

// ABOUTME: A store that can report its knowledge about a var list as a residue
// ABOUTME: and re-impose one — the projection half of tabled constraints.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.goals.Goal;
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
 * the same projection; the store never learns WHICH side it serves, only the
 * STRENGTH demanded ({@code wideningAllowed}).
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
	 * <p>THE CONTRACT: TRANSCRIBE everything expressible — domains and
	 * wholly-covered couplings alike; permission to widen is not an
	 * instruction to widen. {@code wideningAllowed} governs only the
	 * INEXPRESSIBLE remainder (a coupling escaping to an unsupplied var):
	 * dropped silently when allowed — the caller declared the widening sound
	 * on its side (containment: a wider search, filtered at consumption) —
	 * or {@link IllegalStateException} when exactness was demanded: the
	 * parameter carries the boundary's context in, which is what makes the
	 * refusal the store's to raise (tabled-constraints.md §5.1).
	 */
	R project(List<LVar<?>> vars, boolean wideningAllowed);

	/**
	 * This store's knowledge under changed variable names — the SAME sort
	 * (live names to live names): {@link Renaming#walking} normalizes at a
	 * boundary crossing (entries whose var resolves to a value are spent and
	 * drop), {@link Renaming#into} retargets at replay (unseeded vars mint
	 * fresh — the existential). The store IS a residue over its own names;
	 * restatement is {@code rename(r).stated()}.
	 */
	Projectable<R> rename(Renaming renaming);

	/**
	 * This store as a re-expressible goal: impose every item it holds through
	 * the PUBLIC statement entries, so the knowledge propagates like freshly
	 * stated constraints (first examination, watchers, cascade) and
	 * re-verifies against the target state.
	 */
	Goal stated();
}
