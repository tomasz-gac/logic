// ABOUTME: The factor-level constraint protocol: a ConstraintStore holds one domain's
// ABOUTME: constraints and revises only its own factor when bindings arrive.
/**
 * The store protocol — the unit of persistence. A {@link
 * com.tgac.logic.ckanren.store.ConstraintStore} is one constraint domain's factor
 * of the package (finite domains, disequality, projection), living for the whole
 * derivation. When the chokepoint applies a prefix, each store returns a {@link
 * com.tgac.logic.ckanren.store.Revision} — AC-3's REVISE as a value: at most its
 * own replaced factor plus emitted inferences; touching the substitutions or
 * another store's entry is not expressible.
 *
 * <p>This package knows the propagator protocol (stores expose their parked
 * propagators for the cross-store wake; revisions carry inferences) but not the
 * driver — the layering the flat package used to hide. Stores that park
 * propagators (FD, projection) mostly delegate to them; disequality participates
 * wholesale through {@code revise} and parks none.
 * Design: docs/design/capability-constraint-api.md §2.3.
 */
package com.tgac.logic.ckanren.store;
