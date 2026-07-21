package com.tgac.logic.tabling;

// ABOUTME: The per-phase decisions that distinguish streaming tabling (fold and hand
// ABOUTME: out now) from closed/star tabling (capture structure, solve at seal, emit).

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple2;

/**
 * The algorithm plugged into the shared tabling skeleton — anonymous master /
 * consumers / park / completion, which every mode walks identically. Two
 * halves: the DERIVATION ALGEBRA (per-derivation value transitions the
 * skeleton continues with) and the per-entry EMIT lifecycle (an
 * {@link EntryLife} minted with each entry):
 *
 * <pre>
 * EXPLORE   bodyState             the anonymous master's starting package
 *           absorb / capture      a reader takes in an answer / a derivation's
 *                                 contribution is captured for the cell
 * EMIT      entryLife             per entry: sealed — emission for its drained
 *                                 readers; caughtUp — emission for a straggler
 * </pre>
 *
 * {@code Streaming} does all its work during explore (fold each answer's value
 * into the cell, hand it out through the consumers as found) and its entry
 * life is inert; the weight package's closed mode explores for structure only,
 * then solves the star and replays each entry's readers from its life.
 * {@link Tabling} calls these hooks and never branches on the mode, so each
 * algorithm reads in one place.
 */
public interface TablingMode {

	/** The answer cell's ⊕ (fold on dedup): presence for set/closed, the real fold for weighted. */
	IdempotentSemiring<Object> cellSemiring();

	/**
	 * The anonymous master's starting package: fresh, caller-agnostic body
	 * state (running value reset to ONE) — derived from the first caller's
	 * package, whose substitutions carry the call pattern.
	 */
	Package bodyState(Package callerPkg);

	/**
	 * The reader's state after taking in a cached answer it just unified
	 * against the call pattern; {@code enclosingCall} is the tabled call the
	 * consumer runs inside (null at the top level). Streaming ⊗s the cached
	 * cell value into the running value. Closed: reading an OPEN entry records
	 * the loop and tags the delivery a pre-star fragment; reading a SOLVED
	 * entry ⊗s the solved value inline for a coated reader (its capture folds
	 * it in) and stays a fragment for a top-level one (the replay at its
	 * chain's end delivers).
	 */
	Package absorb(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer,
			Object cellValue, TableEntry<Object> enclosingCall);

	/**
	 * The body derived an answer: its contribution, captured as what the cell
	 * caches — the term and its value. The running weight for streaming; bare
	 * presence for closed, which instead captures the derivation's base/edge
	 * on the entry as a side effect.
	 */
	Tuple2<Reified<?>, Object> capture(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm);

	/**
	 * The EMIT lifecycle for one entry, created with the entry and owning its
	 * per-entry emission state ({@link EntryLife}).
	 */
	EntryLife entryLife(TableEntry<Object> entry);
}
