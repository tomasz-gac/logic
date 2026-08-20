package com.tgac.logic.constraints.store;

// ABOUTME: The pricing capability: an atom that can recognize itself as
// ABOUTME: born-violated under partial knowledge. Declared, never assumed.

import com.tgac.logic.goals.Package;

/**
 * Provably failing under the current partial knowledge? A TRUST SURFACE
 * like every bound: the check must be monotone under binding growth —
 * failure found at pricing is failure forever. An atom kind declares this
 * capability when it has a cheap own-semantics check; the activation door
 * reads it, and an atom without it prices as unknown (1, no eager 0).
 */
public interface Doomed {

	boolean doomed(Package state);
}
