package com.tgac.logic.weight;

// ABOUTME: Marks a closed-mode exploration escape — a pre-star fragment the closed
// ABOUTME: collector drops; the seal's replay delivers the finalized answer untagged.

import com.tgac.logic.goals.Packaged;

/**
 * A {@link Packaged} tag on a package that escaped a tabled call during CLOSED
 * (wait-mode) exploration: its value is a fragment, complete only once the star
 * has summed the loops, so {@code solveClosed}'s collector drops it. The seal's
 * reader-chain replay pushes the finalized answer through UNtagged, which the
 * collector keeps (docs/design/star-tabling.md §4.5).
 */
public final class Fragment implements Packaged {
	public static final Fragment MARKER = new Fragment();

	private Fragment() {
	}
}
