package com.tgac.logic.tabling;

// ABOUTME: One entry's EMIT lifecycle: sealed hands over the drained readers,
// ABOUTME: caughtUp a straggler — per-entry state lives on the instance, not in maps.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import io.vavr.collection.List;

/**
 * The per-entry half of a {@link TablingMode}: one instance per
 * {@link TableEntry}, created with it, owning that entry's emission state so
 * the entry's life reads sequentially — sealed, (solved,) emitted — instead
 * of being re-derived from mode-global maps at every hook.
 *
 * <p>Both methods return the EMIT work for the event; the fiber rides the
 * branch that delivered the event and is stepped by the same scheduler drive.
 */
public interface EntryLife {

	/**
	 * The entry sealed: its answers are final and {@code drained} are the
	 * consumers parked on it — dead branches for streaming, emission targets
	 * for closed tabling (whose first-announced life solves the dependency
	 * closure for the whole group).
	 */
	Fiber<Nothing> sealed(List<Registration> drained);

	/**
	 * A consumer caught up with the already-sealed entry — the end of its
	 * chain, arriving after the seal's drain. A finished branch for
	 * streaming; closed tabling replays it with the solved values, now or
	 * when the solve lands.
	 */
	Fiber<Nothing> caughtUp(Registration reader);
}
