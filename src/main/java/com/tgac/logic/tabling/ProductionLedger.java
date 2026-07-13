package com.tgac.logic.tabling;

// ABOUTME: One production's work ledger: the running half as two monotone
// ABOUTME: counters, the sleeping half as outposts — who sleeps where.

import java.util.HashMap;
import java.util.Map;

/**
 * Every piece of a production's work is in one of three states: RUNNING
 * (a live fiber — counted by {@code started}/{@code finished}, the
 * Dijkstra–Scholten pair, both monotone), SLEEPING (parked on some entry —
 * an outpost, wakeable if that entry ever produces), or dead (silence).
 * Completion is both halves reading empty: no running work, and nothing
 * sleeping anywhere that could still wake
 * (docs/design/table-completion.md §4).
 *
 * <p>All state guarded by this monitor; foreign completion flags are read
 * lock-free (upward-closed: a stale false only defers).
 */
final class ProductionLedger {

	private long started;
	private long finished;

	/** Sleeping pieces of this production, mapped to the entry each parks on. */
	private final Map<Registration, TableEntry> outposts = new HashMap<>();

	synchronized void taskStarted() {
		started++;
	}

	synchronized void taskFinished() {
		finished++;
	}

	synchronized void sleeping(Registration r, TableEntry parkedOn) {
		outposts.put(r, parkedOn);
	}

	synchronized void awake(Registration r) {
		outposts.remove(r);
	}

	/**
	 * The self-SCC quiescence check: the production has started, all its
	 * fibers ended, and every sleeper parks HOME (on {@code self} — waking
	 * would need a new self answer, circularly impossible) or on an entry
	 * that will never produce again.
	 */
	synchronized boolean quiescentAndBlockedOnlyBy(TableEntry self) {
		if (started == 0 || finished != started) {
			return false;
		}
		for (Map.Entry<Registration, TableEntry> o : outposts.entrySet()) {
			if (o.getValue() != self && !o.getValue().isComplete()) {
				return false;
			}
		}
		return true;
	}
}
