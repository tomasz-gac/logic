package com.tgac.logic.ckanren.propagator;

// ABOUTME: A narrowing of the values a term may take — the payload of Inference.narrow.
// ABOUTME: The vocabulary-level view of a domain: applicable to a target, nothing more.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;

/**
 * What the cross-factor vocabulary needs from a domain — and nothing else. The
 * finite-domain {@code Domain} hierarchy implements this with all its lattice and
 * arithmetic machinery staying in {@code finitedomain}; the vocabulary only ever
 * applies a narrowing to a term.
 */
public interface Narrowing {

	/**
	 * The goal that applies this narrowing to {@code target}: a membership check for
	 * a ground target; for a variable, an intersection with its current attribution —
	 * a no-op when unchanged, a binding on collapse, a failure when emptied.
	 */
	Goal applyTo(Term<?> target);
}
