package com.tgac.logic.ckanren;

// ABOUTME: Transient per-pass collection of Verdict.run goals, spliced into the search
// ABOUTME: only after the outermost propagation pass quiesces. A plain, inert Store.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import io.vavr.collection.List;

/**
 * Rides the package only for the duration of the outermost chokepoint pass — its
 * presence marks "a pass is in flight", so nested passes append instead of
 * splicing (docs/design/suspensions.md §1: an arbitrary goal must never run
 * mid-propagation).
 */
final class PendingRuns implements Store {

	final List<Goal> runs;

	private PendingRuns(List<Goal> runs) {
		this.runs = runs;
	}

	static PendingRuns empty() {
		return new PendingRuns(List.empty());
	}

	PendingRuns with(Goal goal) {
		return new PendingRuns(runs.append(goal));
	}

	@Override
	public Store remove(Stored c) {
		return this;
	}

	@Override
	public Store prepend(Stored c) {
		return this;
	}

	@Override
	public boolean contains(Stored c) {
		return false;
	}

	@Override
	public String toString() {
		return "pendingRuns" + runs;
	}
}
