// ABOUTME: The constraint engine layer: CKanren's user-facing goals and Propagation,
// ABOUTME: the driver that owns the chokepoint, the agenda, and the suspensions.
/**
 * The driver layer. {@link com.tgac.logic.ckanren.CKanren} is the user-facing
 * surface (constraint-aware unification and reification); {@link
 * com.tgac.logic.ckanren.Propagation} is the engine: the chokepoint every
 * binding routes through ({@code resolve}), the statement entry
 * ({@code activate}), the kernel suspension entry ({@code suspend}), the
 * transient agenda worklist that makes the propagation fixpoint explicit, and
 * the persistent suspension store ripened on watched-chain bindings.
 *
 * <p>Layering: the driver speaks ONLY to stores —
 * {@code com.tgac.logic.ckanren.store} is its entire constraint boundary; how a
 * store computes its answer (FD's propagators, disequality's record
 * verification) is machinery the driver never sees, owned by each domain's
 * package. Concrete stores live with their domains ({@code finitedomain},
 * {@code separate}); {@code projection} is a facade over kernel suspensions.
 * Design: docs/design/minimal-constraint-vocabulary.md.
 */
package com.tgac.logic.ckanren;
