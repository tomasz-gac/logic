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
 * the sleepers parked on it are dead, and each one's ENCLOSING BODY loses
 * its obstruction ("parks on a complete entry" now holds), so it is
 * rechecked.
 * Monitors are never nested: each entry's rule runs under its own locks,
 * the walk happens outside them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Completion {

	/**
	 * Bill {@code work} to the entry whose body it is a line of. A null body
	 * is the TOP-LEVEL QUERY: no ledger, no completion, gates nothing — its
	 * work runs unbilled.
	 */
	static Fiber<Nothing> track(TableEntry body, Fiber<Nothing> work) {
		if (body == null) {
			return work;
		}
		return body.getLedger().counted(work, () -> cascade(body));
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
				TableEntry body = r.getEnclosingBody();
				if (body != null) {
					body.getLedger().awake(r);
					queue.add(body);
				}
			}
		}
	}
}
