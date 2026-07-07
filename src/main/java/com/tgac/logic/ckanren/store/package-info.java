// ABOUTME: THE constraint protocol: a ConstraintStore answers the driver's three
// ABOUTME: triggers with a Revision — its own factor plus cross-store consequences.
/**
 * The store protocol — the driver's entire constraint boundary
 * (docs/design/minimal-constraint-vocabulary.md §2.1). A {@link
 * com.tgac.logic.ckanren.store.ConstraintStore} is one constraint domain's factor
 * of the package (finite domains, disequality, projection), living for the whole
 * derivation. Three triggers — {@code revise} (bindings arrived), {@code changed}
 * (a term changed; broadcast), {@code stated} (your item was stated; dispatched to
 * the owner) — all answer a {@link com.tgac.logic.ckanren.store.Revision}: at
 * most the store's own replaced factor, plus consequences in the driver's
 * two-word vocabulary (inferred {@code Prefix}es, changed {@code Term}s) and run
 * goals for the post-quiescence splice. Touching the substitutions or another
 * store's entry is not expressible.
 *
 * <p>How a store computes its revision is its own business — FD and projection
 * schedule parked bodies with the {@code ckanren.propagator} toolkit; disequality
 * re-verifies its records wholesale.
 */
package com.tgac.logic.ckanren.store;
