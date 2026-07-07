// ABOUTME: The constraint engine layer: CKanren's user-facing goals and Propagation,
// ABOUTME: the chokepoint driver that interprets both constraint protocols.
/**
 * The driver layer. {@link com.tgac.logic.ckanren.CKanren} is the user-facing
 * surface (constraint-aware unification and reification); {@link
 * com.tgac.logic.ckanren.Propagation} is the engine — the chokepoint every
 * binding routes through, the agenda worklist that makes the propagation
 * fixpoint explicit, and the interpreter of verdicts, revisions and inferences.
 *
 * <p>Layering: this package depends on both protocol packages —
 * {@code com.tgac.logic.ckanren.propagator} (the item protocol, bottom) and
 * {@code com.tgac.logic.ckanren.store} (the factor protocol, which knows the
 * item protocol but not this driver). Concrete stores live with their domains
 * ({@code finitedomain}, {@code separate}, {@code projection}).
 * Design: docs/design/capability-constraint-api.md.
 */
package com.tgac.logic.ckanren;
