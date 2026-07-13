package com.tgac.logic.tabling;

// ABOUTME: The join rule and its cascade: an entry completes when its ledger
// ABOUTME: is quiescent; completions unblock producers of the dead sleepers.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import io.vavr.collection.List;
import java.util.ArrayDeque;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Completion detection's two moving parts, in one place:
 *
 * <p>{@link #track} routes every unit of production work through the
 * ledger's pairing discipline exactly once, hooking the cascade to each
 * finish.
 *
 * <p>{@link #cascade} walks completions bottom-up: when an entry completes,
 * the sleepers parked on it are dead, and each one's ENCLOSING CALL loses
 * its obstruction ("parks on a complete entry" now holds), so it is
 * rechecked.
 * Monitors are never nested: each entry's rule runs under its own locks,
 * the walk happens outside them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Completion {

	/**
	 * Bill {@code work} to the call whose execution it is part of. A null
	 * call is the TOP-LEVEL QUERY: no ledger, no completion, gates nothing —
	 * its work runs unbilled.
	 */
	static Fiber<Nothing> track(TableEntry enclosingCall, Fiber<Nothing> work) {
		if (enclosingCall == null) {
			return work;
		}
		return enclosingCall.getLedger().counted(work, () -> cascade(enclosingCall));
	}

	static void cascade(TableEntry entry) {
		ArrayDeque<TableEntry> queue = new ArrayDeque<>();
		queue.add(entry);
		while (!queue.isEmpty()) {
			List<Registration> dead = queue.poll().completeIfQuiescent();
			if (dead == null) {
				continue;
			}
			for (Registration r : dead) {
				TableEntry enclosingCall = r.getEnclosingCall();
				if (enclosingCall != null) {
					enclosingCall.getLedger().awake(r);
					queue.add(enclosingCall);
				}
			}
		}
	}
}
