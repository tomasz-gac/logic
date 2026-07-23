package com.tgac.logic.tabling;

// ABOUTME: The per-phase decisions that distinguish streaming tabling (fold and hand
// ABOUTME: out now) from closed/star tabling (capture structure, solve at seal, emit).

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple2;
import io.vavr.collection.List;

/**
 * The algorithm plugged into the shared tabling skeleton — anonymous master /
 * consumers / park / completion, which every mode walks identically. Two
 * halves: the DERIVATION ALGEBRA (per-derivation value transitions the
 * skeleton continues with) and the EMIT events (per entry, both returning the
 * emission work that rides the delivering branch):
 *
 * <pre>
 * EXPLORE   bodyState             the anonymous master's starting package
 *           absorb / capture      a reader takes in an answer / a derivation's
 *                                 contribution is captured for the cell
 * EMIT      sealed / caughtUp     the entry's answers went final — emission for
 *                                 its drained readers / for a straggler
 * </pre>
 *
 * {@code Streaming} does all its work during explore (fold each answer's value
 * into the cell, hand it out through the consumers as found) and its EMIT
 * events are inert; the weight package's closed mode explores for structure
 * only, then solves the star and replays each entry's readers — organizing its
 * per-entry state in Life objects it manages internally. {@link Tabling} calls
 * these hooks and never branches on the mode, so each algorithm reads in one
 * place.
 */
public interface TablingMode {

	/** The answer cell's ⊕ (fold on dedup): presence for set/closed, the real fold for weighted. */
	IdempotentSemiring<Object> cellSemiring();

	/**
	 * Whether answers may carry constraint residues. Streaming supports them
	 * (replay restates at consumption); the closed/star mode does not — weights
	 * over conditional answers is an undesigned, orthogonal interaction,
	 * refused loudly at produce.
	 */
	default boolean supportsConstrainedAnswers() {
		return false;
	}

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
	 * The entry sealed: its answers are final and {@code drained} are the
	 * consumers parked on it — dead branches for streaming, emission targets
	 * for closed tabling (whose first-announced entry of a fully marked group
	 * solves the dependency closure for the whole group).
	 */
	Fiber<Nothing> sealed(TableEntry<Object> entry, List<Registration> drained);

	/**
	 * A consumer caught up with the already-sealed entry — the end of its
	 * chain, arriving after the seal's drain. A finished branch for
	 * streaming; closed tabling replays it with the solved values, now or
	 * when the solve lands.
	 */
	Fiber<Nothing> caughtUp(TableEntry<Object> entry, Registration reader);
}
