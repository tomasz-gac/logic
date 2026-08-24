package com.tgac.logic.constraints.store;

// ABOUTME: A theory operation's result plus exactly the atoms that changed -
// ABOUTME: what the doors read to decide whether and what to re-normalize.

import io.vavr.collection.LinkedHashSet;
import lombok.Value;

/**
 * The report a {@link Theory} operation answers when the caller needs to know
 * WHAT moved, not just the result: the revised theory, and exactly the atoms
 * that changed in it — inserted, fused to a new occupant, or rewritten by a
 * crossing. Covered, duplicate and domination-killed atoms are absent. An
 * empty {@code changed} means the operation moved nothing and the theory is
 * the receiver by identity — the door may skip entirely.
 */
@Value
public class Revised<F extends Factor<F>> {
	Theory<F> theory;
	LinkedHashSet<Atom<F>> changed;
}
