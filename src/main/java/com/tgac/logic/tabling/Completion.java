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
 * the sleepers parked on it are dead, and each one's PRODUCER loses its
 * obstruction ("parks on a complete entry" now holds), so it is rechecked.
 * Monitors are never nested: each entry's rule runs under its own locks,
 * the walk happens outside them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Completion {

	/** Count {@code work} as one unit of {@code production}'s running work. */
	static Fiber<Nothing> track(TableEntry production, Fiber<Nothing> work) {
		return production.getLedger().counted(work, () -> cascade(production));
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
				TableEntry producer = r.getProducer();
				if (producer != null) {
					producer.getLedger().awake(r);
					queue.add(producer);
				}
			}
		}
	}
}
