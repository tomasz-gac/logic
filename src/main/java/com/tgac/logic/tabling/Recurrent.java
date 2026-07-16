package com.tgac.logic.tabling;

// ABOUTME: Marks a derivation that consumed a still-open (looping) call — set by
// ABOUTME: consume in wait mode, read at produce to tell a coefficient from a base.

import com.tgac.logic.goals.Packaged;

/**
 * A {@link Packaged} tag on a package whose derivation went through a
 * {@code consume} of a not-yet-sealed call — it LOOPED. Set by consume in
 * CLOSED (wait) mode; read at the produce hook to tell a recursive derivation
 * (coefficient, carried by the sleeper) from a non-recursive one (base —
 * stashed on the entry). It rides the immutable package, so it is per-derivation
 * and order-proof (docs/design/star-tabling.md §4.1).
 */
public final class Recurrent implements Packaged {
	public static final Recurrent MARKER = new Recurrent();

	private Recurrent() {
	}
}
