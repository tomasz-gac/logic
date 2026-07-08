// ABOUTME: THE constraint protocol: a ConstraintStore answers the driver's three
// ABOUTME: triggers with a Revision — its own factor plus cross-store consequences.
/**
 * The store protocol — the driver's entire constraint boundary
 * (docs/design/minimal-constraint-vocabulary.md §2.1). A {@link
 * com.tgac.logic.ckanren.store.ConstraintStore} is one constraint domain's factor
 * of the package (finite domains, disequality, projection), living for the whole
 * derivation. Two triggers — {@code revise} (bindings arrived: custody, your own
 * watchers, your own cascade) and {@code stated} (your item was stated; dispatched
 * to the owner) — answer a {@code Fiber} of
 * {@link com.tgac.logic.ckanren.store.Revision}: at
 * most the store's own replaced factor, plus consequences in the driver's
 * two-word vocabulary (inferred {@code Prefix}es, narrowed {@code Term}s) and run
 * goals for the post-quiescence splice. Touching the substitutions or another
 * store's entry is not expressible.
 *
 * <p>How a store computes its revision is its own business — FD administers its
 * own propagators, projection parks bare (term, body) suspensions, disequality
 * re-verifies its records wholesale. {@link
 * com.tgac.logic.ckanren.store.Watches} is the shared chain-inclusive matcher.
 */
package com.tgac.logic.ckanren.store;
