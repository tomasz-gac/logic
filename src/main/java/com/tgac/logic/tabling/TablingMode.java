package com.tgac.logic.tabling;

// ABOUTME: The per-phase decisions that distinguish streaming tabling (fold and hand
// ABOUTME: out now) from closed/star tabling (capture structure, solve at seal, emit).

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;

/**
 * The algorithm plugged into the shared tabling skeleton — master / consumer /
 * park / completion, which every mode walks identically. The hooks are the
 * phases of a tabled call's life:
 *
 * <pre>
 * EXPLORE   onExplore              the master claims the call, the body begins
 *           onConsume / onProduce  a derivation reads or derives an answer
 *           onExit                 an answer crosses back into the caller
 * SEAL      onSeal                 the entry's answers are final
 * EMIT      the fiber onSeal returns
 * </pre>
 *
 * {@code Streaming} does all its work during explore (fold each answer's value
 * into the cell, hand it out as found) and its seal is inert; the weight
 * package's closed mode explores for structure only, then solves the star and
 * emits from {@code onSeal}. {@link Tabling} calls these hooks and never
 * branches on the mode, so each algorithm reads in one place. Coat
 * (EnclosingCall) handling is the skeleton's job, not a mode's: packages arrive
 * at these hooks already wearing the right call.
 */
public interface TablingMode {

	/** The answer cell's ⊕ (fold on dedup): presence for set/closed, the real fold for weighted. */
	IdempotentSemiring<Object> cellSemiring();

	/**
	 * EXPLORE begins: the master has claimed {@code entry} and its body is about
	 * to run. Returns the fresh, caller-agnostic body state (running value reset
	 * to ONE). A mode that acts at seal records here what emit will need —
	 * {@code k} is the master's continuation, {@code callerEntry} the tabled call
	 * it was claimed from (null at the top level).
	 */
	Package onExplore(TableEntry<Object> entry, Fiber.Fn<Package, Nothing> k,
			Package callerPkg, Unifiable<?> argsTerm, TableEntry<Object> callerEntry);

	/**
	 * A consumer has unified a cached answer against the call pattern. Streaming
	 * ⊗s the cached cell value into the running value; closed records that this
	 * derivation consumed a (still-open) SCC answer — a loop.
	 */
	Package onConsume(Package unifiedPkg, TableEntry<Object> entry, Reified<?> consumedAnswer, Object cellValue);

	/**
	 * The body derived an answer. Returns what the cell caches — the term and its
	 * value: the running weight for streaming, bare presence for closed, which
	 * instead captures the derivation's base/edge on the entry as a side effect.
	 */
	Tuple2<Reified<?>, Object> onProduce(TableEntry<Object> entry, Package answerPkg, Reified<?> answerTerm);

	/**
	 * The answer leaves {@code entry}'s body back to its caller — already
	 * re-coated to the caller by the skeleton. Streaming ⊗s the caller's running
	 * value with {@code value} onto it. Closed restores the caller's context (its
	 * store and loop-record from {@code callerPkg}) and records that the caller
	 * consumed {@code (entry, answerTerm)} — the produce→caller half of edge
	 * capture, mirroring {@link #onConsume} — then tags it a pre-star escape to drop.
	 */
	Package onExit(Package answerPkg, TableEntry<Object> entry, Reified<?> answerTerm, Package callerPkg, Object value);

	/**
	 * SEAL: {@code entry}'s answers are final; no derivation can add one. The
	 * returned fiber is EMIT — it rides the sealing branch and is stepped by the
	 * same scheduler drive. Inert for streaming; closed solves each fully sealed
	 * dependency closure here and replays the recorded continuations with
	 * {@code x = A* ⊗ b}.
	 */
	Fiber<Nothing> onSeal(TableEntry<Object> entry);
}
