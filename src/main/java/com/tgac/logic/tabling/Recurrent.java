package com.tgac.logic.tabling;

// ABOUTME: The SCC-answers a derivation consumed while looping — set by consume in
// ABOUTME: wait mode, read at produce to route the value to base / edge / nonlinear.

import com.tgac.logic.goals.Packaged;
import com.tgac.logic.unification.Reified;
import io.vavr.collection.List;

/**
 * Records the still-open (looping) answers a derivation consumed, in CLOSED
 * (wait) mode. {@code consume} extends it; the produce hook reads the count:
 * 0 consumed → the derivation is a BASE, 1 → an EDGE coefficient to that answer,
 * ≥2 → NONLINEAR recursion (outside star's reach). It rides the IMMUTABLE
 * package, so it is per-derivation and order-proof
 * (docs/design/star-tabling.md §4).
 */
public final class Recurrent implements Packaged {
	public static final Recurrent NONE = new Recurrent(List.empty());

	public final List<Reified<?>> consumed;

	private Recurrent(List<Reified<?>> consumed) {
		this.consumed = consumed;
	}

	public Recurrent and(Reified<?> answer) {
		return new Recurrent(consumed.prepend(answer));
	}
}
