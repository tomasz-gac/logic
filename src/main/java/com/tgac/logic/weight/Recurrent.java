package com.tgac.logic.weight;

// ABOUTME: The equation-graph nodes a derivation consumed while looping — set by
// ABOUTME: absorb, read at capture to route the value to base / edge / nonlinear.

import com.tgac.logic.goals.Packaged;
import io.vavr.collection.List;

/**
 * Records the still-open (looping) {@link Node}s a derivation consumed, in CLOSED
 * (wait) mode. {@code absorb} extends it; {@code capture} reads the count:
 * 0 consumed → the derivation is a BASE, 1 → an EDGE coefficient to that node,
 * ≥2 → NONLINEAR recursion (outside star's reach). It rides the IMMUTABLE
 * package, so it is per-derivation and order-proof (docs/design/star-tabling.md §4).
 */
public final class Recurrent implements Packaged {
	public static final Recurrent NONE = new Recurrent(List.empty());

	public final List<Node> consumed;

	private Recurrent(List<Node> consumed) {
		this.consumed = consumed;
	}

	public Recurrent and(Node node) {
		return new Recurrent(consumed.prepend(node));
	}
}
