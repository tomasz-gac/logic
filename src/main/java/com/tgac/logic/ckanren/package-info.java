// ABOUTME: The constraint engine layer: CKanren's user-facing goals and Propagation,
// ABOUTME: the chokepoint driver that interprets both constraint protocols.
/**
 * The driver layer. {@link com.tgac.logic.ckanren.CKanren} is the user-facing
 * surface (constraint-aware unification and reification); {@link
 * com.tgac.logic.ckanren.Propagation} is the engine — the chokepoint every
 * binding routes through, the agenda worklist that makes the propagation
 * fixpoint explicit, and the router of revisions.
 *
 * <p>Layering: the driver speaks ONLY to stores —
 * {@code com.tgac.logic.ckanren.store} is its entire constraint boundary; the
 * {@code com.tgac.logic.ckanren.propagator} toolkit is store-internal machinery
 * the driver never sees. Concrete stores live with their domains
 * ({@code finitedomain}, {@code separate}, {@code projection}).
 * Design: docs/design/minimal-constraint-vocabulary.md.
 */
package com.tgac.logic.ckanren;
