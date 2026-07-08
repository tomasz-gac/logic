// ABOUTME: The propagator toolkit: a library stores use INTERNALLY to schedule parked
// ABOUTME: constraint bodies. The driver never sees a propagator or a verdict.
/**
 * The propagator toolkit — used BY stores, unknown to the driver
 * (docs/design/minimal-constraint-vocabulary.md §2.4). A {@link
 * com.tgac.logic.ckanren.propagator.Propagator} is one parked constraint body
 * watching terms; the store that owns it runs it from its {@code narrowed} and
 * {@code stated} hooks and administers the {@link
 * com.tgac.logic.ckanren.propagator.Verdict} itself: keep parked (the
 * default-safe case — forgetting to re-park is not expressible), subsumed, fail
 * the branch, update the owning factor, or hand a goal to the run lane. The
 * administration folds into the one {@code Revision} the store answers, so
 * nothing intra-store ever crosses the driver boundary.
 *
 * <p>Stores with nothing parked (disequality) never touch this package.
 */
package com.tgac.logic.ckanren.propagator;
