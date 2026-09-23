package org.clauseway.logic.constraints.store;

// ABOUTME: The refutation capability: an atom that can recognize itself as
// ABOUTME: born-violated under partial knowledge. Declared, never assumed.

import org.clauseway.logic.goals.Package;

/**
 * Provably failing under the current partial knowledge? A TRUST SURFACE:
 * the check must be monotone under binding growth — doom found now is
 * doom forever. An atom kind declares this capability when it has a cheap
 * own-semantics check; the activation door reads it, the doom pruning
 * pass consumes it, and an atom without it claims nothing.
 */
public interface Doomed {

	boolean doomed(Package state);
}
