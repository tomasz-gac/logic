package com.tgac.logic.tabling;

// ABOUTME: The per-phase decisions that distinguish streaming tabling (fold and hand
// ABOUTME: out by finality) from closed/star tabling (capture structure, solve at seal).

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple2;

/**
 * The algorithm plugged into the shared tabling skeleton — anonymous master /
 * consumers / park / completion, which every mode walks identically. Two
 * halves: the DERIVATION ALGEBRA (per-derivation value transitions the
 * skeleton continues with) and the EMIT events:
 *
 * <pre>
 * EXPLORE   bodyState             the anonymous master's starting package
 *           absorb / capture      a reader takes in an answer / a derivation's
 *                                 contribution is captured for the cell
 * EMIT      caughtUp              a straggler arrived after the seal
 * </pre>
 *
 * The CELL is one {@link JoinMap} for every mode: term → value in the mode's
 * {@link #cellSemiring}, and delivery timing is the VALUES' OWN FINALITY —
 * a value at ⊕'s top ({@code 1 ⊕ a = 1}, bounded) is final on arrival and
 * streams, anything below is provisional until the seal. {@code Streaming}
 * folds real values during explore (conditions for plain tabling, the
 * weight ring for bounded-weighted); the weight package's closed mode
 * explores for structure only (every capture is 1), then solves the star
 * and replays each entry's readers. {@link Tabling} calls these hooks and
 * never branches on which mode it is.
 */
public interface TablingMode {

	/** The answer cell's ring: conditions for plain/closed, the weight ring for bounded-weighted. */
	IdempotentSemiring<Object> cellSemiring();

	/**
	 * The anonymous master's starting package: fresh, caller-agnostic body
	 * state (running value reset to ONE) — derived from the first caller's
	 * package, whose substitutions carry the call pattern.
	 */
	Package bodyState(Package callerPkg);

	/**
	 * The reader's state after taking in a cached answer it just unified
	 * against the call pattern. Streaming ⊗s the cached cell value into the
	 * running value (plain has nothing to thread — the condition was
	 * imposed by the delivery's restate). Closed: reading an OPEN entry
	 * records the loop and tags the delivery a pre-star fragment; reading a
	 * SOLVED entry ⊗s the solved value inline for a reader inside a body
	 * (its capture folds it in) and stays a fragment for a top-level one
	 * (the replay at its chain's end delivers).
	 */
	Package absorb(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer,
			Object cellValue);

	/**
	 * The body derived an answer: the term and the VALUE the cell caches
	 * for it. Plain tabling folds the residues into a {@link Condition}
	 * (ground = 1); weighted reads the running value off the package and
	 * refuses residues (weights over conditional answers is an undesigned,
	 * orthogonal interaction); closed captures the derivation's base/edge
	 * on the entry as a side effect and caches 1.
	 */
	Tuple2<Reified<?>, Object> capture(TableEntry<Object> entry, Package answerPkg,
			Reified<?> answerTerm, Residues residues);

	/**
	 * A consumer caught up with the already-sealed entry — the end of its
	 * chain, arriving after the seal's drain. A finished branch for
	 * streaming; closed tabling replays it with the solved values, now or
	 * when the solve lands.
	 */
	Fiber<Nothing> caughtUp(TableEntry<Object> entry, Reader reader);
}
