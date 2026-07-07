package com.tgac.logic.ckanren;

// ABOUTME: A constraint body that reports a Verdict instead of administering its own
// ABOUTME: parked lifecycle — the framework parks, removes and re-wakes.

import com.tgac.logic.unification.Package;

/**
 * Re-examines a constraint against the current state. Reads anything, mutates
 * nothing; the outcome is the returned {@link Verdict}. Under the step-1 adapter
 * wake matching still uses the parked {@link Constraint}'s walked args; the
 * capability driver (docs/design/capability-constraint-api.md §2.5) adds an
 * explicit watched-variable set.
 */
@FunctionalInterface
public interface Propagator {

	Verdict propagate(Package state);
}
