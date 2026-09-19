// ABOUTME: THE constraint protocol: a ConstraintStore answers the driver's two
// ABOUTME: triggers with a Fiber of Revision — its own factor plus consequences.
/**
 * The store protocol — the driver's entire constraint boundary
 * (docs/reference/constraint-kernel.md). A {@link
 * org.clauseway.logic.constraints.store.Factor} is one constraint domain's
 * factor of the package (finite domains, disequality), living for the whole
 * derivation. Two triggers — {@code revise} (bindings arrived: custody, your
 * own watchers, your own cascade) and {@code stated} (your item was stated;
 * dispatched to the owner) — answer a {@code Fiber} of {@link
 * org.clauseway.logic.constraints.store.Revision}: at most the store's own replaced
 * factor, plus consequences in the driver's vocabulary — inferred
 * {@code Prefix}es (knowledge) and {@link
 * org.clauseway.logic.constraints.store.Suspension}s (search effects; the degenerate
 * always-ripe form is a plain run). Touching the substitutions or another
 * store's entry is not expressible.
 *
 * <p>How a store computes its revision is its own business — FD administers
 * its own propagators, disequality re-verifies its records wholesale. {@link
 * org.clauseway.logic.constraints.store.Watches} is the shared chain-inclusive matcher;
 * suspension conditions are scoped to the {@code Substitutions} view and must
 * be monotone (see {@code Suspension}).
 */
package org.clauseway.logic.constraints.store;
