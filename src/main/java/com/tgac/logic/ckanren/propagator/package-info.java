// ABOUTME: The item-level constraint protocol: a Propagator watches terms and rules
// ABOUTME: on its own lifecycle; Inference is the cross-factor vocabulary it emits.
/**
 * The propagator protocol — the unit of scheduling. A {@link
 * com.tgac.logic.ckanren.propagator.Propagator} is one parked constraint watching
 * terms; when a watched term changes, the driver re-runs it and administers its
 * {@link com.tgac.logic.ckanren.propagator.Propagator.Verdict}: the propagator rules only on
 * its own lifecycle (keep parked, subsumed, fail the branch, splice a goal) and
 * emits {@link com.tgac.logic.ckanren.propagator.Inference}s — the only vocabulary
 * for information crossing package factors ({@code bind} grows the substitution,
 * {@code narrow} shrinks a term through the {@link
 * com.tgac.logic.ckanren.propagator.Narrowing} seam).
 *
 * <p>This package is the bottom layer of the constraint machinery: it knows
 * nothing of stores or the driver. The store protocol
 * ({@code com.tgac.logic.ckanren.store}) and the engine
 * ({@code com.tgac.logic.ckanren.Propagation}) depend on it, never the reverse.
 * Design: docs/design/capability-constraint-api.md §2.2, §2.4.
 */
package com.tgac.logic.ckanren.propagator;
