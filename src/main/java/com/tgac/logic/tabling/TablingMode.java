package com.tgac.logic.tabling;

// ABOUTME: The per-step decisions that distinguish streaming tabling (fold and hand
// ABOUTME: out now) from closed/star tabling (capture structure, solve at seal, emit).

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;

/**
 * The algorithm plugged into the shared tabling skeleton — master / consumer /
 * park / completion, which every mode walks identically. {@code Streaming} folds
 * each answer's value into the cell and hands it out as it is found (plain and
 * bounded-weighted tabling); the weight package's closed mode explores for
 * structure, solves the star at each seal, and emits then. {@link Tabling} calls
 * these hooks and never branches on the mode, so each algorithm reads in one
 * place. Coat (EnclosingCall) handling is the skeleton's job, not a mode's:
 * packages arrive at these hooks already wearing the right call.
 */
public interface TablingMode {

	/** The answer cell's ⊕ (fold on dedup): presence for set/closed, the real fold for weighted. */
	IdempotentSemiring<Object> cellSemiring();

	/** Master boundary: a fresh, caller-agnostic body state (running value reset to ONE). */
	Package enterBody(Package callerPkg);

	/** The caller's running value at this call site — ⊗ into each answer on the way out. */
	Object callerValue(Package callerPkg);

	/**
	 * A consumer has unified a cached answer against the call pattern. Streaming
	 * ⊗s the cached cell value into the running value; closed records that this
	 * derivation consumed a (still-open) SCC answer — a loop.
	 */
	Package onConsume(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer, Object cellValue);

	/** The value to cache for a produced answer: the running weight (streaming) or presence (closed). */
	Object cacheValue(Package answerPkg);

	/** Produce-time side effect — closed captures the derivation's base/edge; returns the term to cache. */
	Reified<?> onProduce(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm);

	/**
	 * The answer leaving {@code entry}'s body back to its caller — already
	 * re-coated to the caller by the skeleton. Streaming ⊗s the caller's running
	 * value with {@code value} onto it. Closed restores the caller's context (its
	 * store and loop-record from {@code callerPkg}) and records that the caller
	 * consumed {@code (entry, answerTerm)} — the produce→caller half of edge
	 * capture, mirroring {@link #onConsume} — then tags it a pre-star escape to drop.
	 */
	Package onExit(Package answerPkg, TableEntry<Object> entry, Reified<?> answerTerm, Package callerPkg, Object value);

	/**
	 * Wire this master's seal behaviour: closed solves-and-emits, streaming does
	 * nothing. {@code callerEntry} is the tabled call the master was called from,
	 * or null at the top level.
	 */
	void onMasterClaim(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, TableEntry<Object> callerEntry);
}
