package com.tgac.logic.constraints.store;

// ABOUTME: The arrival capability: a store whose knowledge can be met in bulk and
// ABOUTME: re-normalized — bulk-loadable without being table-compatible.

import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;

/**
 * A store that can receive a whole FACTOR of knowledge at once:
 * {@code Propagation.absorb} meets the factor into the resident store over
 * the store's own {@link MeetSemilattice} and queues {@link #normalize} —
 * the trigger family's third row, after bindings ({@code revise}) and
 * single items ({@code stated}). This is the capability alone — bulk
 * loading a fact table, delivering an external result batch, joining a
 * sibling world's knowledge. {@link Projectable} extends it with the
 * DEPARTURE half (split, rename): a store must cross packages both ways to
 * participate in tabling, but bulk-loadable does not imply table-compatible.
 */
public interface Absorbable<S extends Absorbable<S>> extends ConstraintStore, MeetSemilattice<S> {

	/**
	 * Re-establish normal form against {@code state} after a meet brought in
	 * foreign knowledge: re-verify it (a violated record or an out-of-domain
	 * binding FAILS), take first examinations, run the internal fixpoint.
	 * Same scheduling and routing contract as {@code revise}. A met factor
	 * answers no queries before its normalization ran — meet is completed by
	 * normalize.
	 */
	Fiber<Revision> normalize(Package state);
}
