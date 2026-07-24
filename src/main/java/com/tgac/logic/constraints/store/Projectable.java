package com.tgac.logic.constraints.store;

// ABOUTME: A store whose knowledge can change variable namespaces: a semilattice
// ABOUTME: with rename and split — keys, seeding and answer replay are compositions.

import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import io.vavr.Tuple2;
import java.util.List;

/**
 * The boundary capability, single-sorted: a store IS a residue over its own
 * names — live {@link LVar}s or canonical {@link com.tgac.logic.unification.Hole}s
 * alike — and every boundary operation is a composition of three primitives
 * over the store's own {@link MeetSemilattice}:
 *
 * <pre>
 * key projection   = split(callVars)._1.rename(canonical)     — {@link #project}
 * master seeding   = key.rename(ofSlots(callVars)).stated()
 * answer capture   = rename(walking(home))                    — normalization
 * answer replay    = rename(into(seeds)).stated()             — ∃ by minting
 * </pre>
 *
 * Comparison (subsumption keys, entailment matching, dedup) is the lattice
 * order the store already has; a hole-named store compares structurally
 * across packages because holes are canonical names. There is no widening
 * parameter and no exactness refusal: keys widen by construction (the
 * caller keeps {@code split}'s covered half), and answers carry the whole
 * factor. Participation in tabling requires this capability — knowledge
 * that cannot cross namespaces cannot be keyed or cached, and unkeyed
 * knowledge means silently wrong reuse.
 *
 * <p>TERMINATION is a separate, undeclared concern: a store whose canonical
 * images over a fixed var list form a finite lattice bounds the tabling
 * ascent (FD does); one that does not (record sets over unbounded values)
 * can ascend forever on adversarial programs — the author's responsibility,
 * exactly like tabling an unbounded generator.
 */
public interface Projectable<S extends Projectable<S>> extends ConstraintStore, MeetSemilattice<S> {

	/**
	 * Lossless factoring: (the knowledge expressible over {@code vars}, the
	 * remainder) — {@code _1 ∧ _2 = this}. The store decides what is
	 * separable (custody); the CALLER decides what to do with the halves:
	 * keys keep {@code _1} and discard the caller-private remainder.
	 */
	Tuple2<S, S> split(List<LVar<?>> vars);

	/**
	 * This store's knowledge under changed names — {@link Renaming#walking}
	 * normalizes (entries whose name resolves to a value are spent and
	 * drop), {@link Renaming#into} retargets at replay (unseeded vars mint
	 * fresh — the existential), {@link Renaming#canonical} and
	 * {@link Renaming#ofSlots} convert live↔canonical.
	 */
	S rename(Renaming renaming);

	/**
	 * This store as a re-expressible goal: impose every item it holds
	 * through the PUBLIC statement entries, so the knowledge propagates like
	 * freshly stated constraints (first examination, watchers, cascade) and
	 * re-verifies against the target state. Valid on LIVE-named knowledge —
	 * a contract, not a type.
	 */
	Goal stated();

	/**
	 * This store's knowledge about {@code vars} in canonical names, slot i ↔
	 * vars[i] — the comparable key citizen. Projecting the empty list of an
	 * empty store is the empty store: the triviality test is {@code isEmpty}.
	 */
	default S project(List<LVar<?>> vars) {
		return split(vars)._1.rename(Renaming.canonical(vars));
	}
}
