package com.tgac.logic.goals;

// ABOUTME: A value that rides the immutable Package keyed by its class — copied
// ABOUTME: on branch, so backtracking gives each derivation its own isolated copy.

/**
 * Citizenship in the {@link Package}: a payload the package carries through the
 * search, keyed by its concrete class, persistent so each branch keeps its own.
 * This is what {@link com.tgac.logic.debug.DebugStore the tracer},
 * {@link com.tgac.logic.tabling.Table the table}, the optimizer and the
 * enclosing-call coat all actually need — riding the package, not participating
 * in constraint solving. {@link Store} is the specialization that additionally
 * holds {@link Stored} records.
 */
public interface Packaged {
}
