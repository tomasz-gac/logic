package com.tgac.logic.tabling;

// ABOUTME: Marks a wait-mode exploration escape — a pre-star fragment the closed
// ABOUTME: collector drops; emit re-produces the finalized answer untagged.

import com.tgac.logic.goals.Packaged;

/**
 * A {@link Packaged} tag on a package that escaped a tabled call during CLOSED
 * (wait-mode) exploration: its value is a fragment, complete only once the star
 * has summed the loops, so {@code solveClosed}'s collector drops it. Emit pushes
 * the finalized answer through UNtagged, which the collector keeps
 * (docs/design/star-tabling.md §4.1).
 */
public final class Exploration implements Packaged {
	public static final Exploration MARKER = new Exploration();

	private Exploration() {
	}
}
