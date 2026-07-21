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
 * consumers / park / completion, which every mode walks identically. Each
 * method is one phase-transition of a derivation's or reader's state, and the
 * skeleton continues with what it returns:
 *
 * <pre>
 * EXPLORE   bodyState             the anonymous master's starting package
 *           absorb / capture      a reader takes in an answer / a derivation's
 *                                 contribution is captured for the cell
 * EMIT      emit / emitCaughtUp   the entry sealed: emission for its drained
 *                                 readers / for a reader that caught up after
 * </pre>
 *
 * {@code Streaming} does all its work during explore (fold each answer's value
 * into the cell, hand it out through the consumers as found) and its emission
 * is inert; the weight package's closed mode explores for structure only, then
 * solves the star and replays the drained consumers from {@code emit}.
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
	 * EMIT at the seal: {@code entry}'s answers are final; no derivation can
	 * add one, and {@code drained} are the consumers parked on it — dead
	 * branches for streaming, emission targets for closed. The returned fiber
	 * rides the sealing branch and is stepped by the same scheduler drive.
	 * Inert for streaming; closed solves each fully sealed dependency closure
	 * here and replays the drained consumers with {@code x = A* ⊗ b}.
	 */
	Fiber<Nothing> emit(TableEntry<Object> entry, List<Registration> drained);

	/**
	 * EMIT for a straggler: a consumer caught up with a SEALED entry — the end
	 * of its chain (the other ending is being drained at the seal itself,
	 * handed to {@link #emit}). For streaming the answers already flowed
	 * inline — the reader is a finished branch. Closed replays a top-level
	 * reader from index 0 with the solved values, now or when the entry's
	 * closure solves; a coated reader's contribution rides its captured edges
	 * instead.
	 */
	Fiber<Nothing> emitCaughtUp(TableEntry<Object> entry, Registration reader);
}
