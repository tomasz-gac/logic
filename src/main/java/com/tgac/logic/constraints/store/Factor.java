package com.tgac.logic.constraints.store;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import io.vavr.collection.LinkedHashSet;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;

/**
 * A family's execution behavior: the interpreter half of a {@link Constraint}
 * pair. The knowledge lives in the pair's {@link Theory}, handed to every
 * trigger — never in the factor; a factor may keep private state, but only
 * as a memo reconstructible from the theory by invariant.
 */
public interface Factor<S extends Factor<S>> extends Packaged {

	/**
	 * Re-establish normal form against {@code state} after knowledge moved:
	 * {@code focus} is what ARRIVED — the posted atom, an absorbed theory's
	 * atoms — and the ONE LAW binds the focused pass to the full one:
	 * {@code normalize(T, F, P) == normalize(T, T.atoms(), P)} whenever the
	 * focus contains the true change. A family may skip only what the focus
	 * cannot have touched; doing more is always sound (the nogood family
	 * verifies wholesale regardless). Verification fails the branch, first
	 * examinations run, the internal fixpoint drains — the door's meet is
	 * completed by this trigger, and a met theory answers no queries before
	 * it ran.
	 */
	Fiber<Revision> normalize(Theory<S> theory, LinkedHashSet<Atom<S>> focus, Package state);

	/**
	 * Revise this store against newly applied bindings — AC-3's REVISE, cKanren's
	 * process-prefix. The chokepoint has already applied the extension; the store
	 * may read anything and change only its own entry — a whole package is not
	 * expressible in the return type. The reaction is COMPLETE: custody checks,
	 * re-examining this store's own watchers of the newly bound variables, and
	 * chasing the resulting cascade are all this store's business; the driver
	 * routes only the returned consequences (inferred prefixes, runs).
	 *
	 * <p>The fiber return is the scheduling contract: cheap reactions return
	 * {@code Fiber.done(revision)}; expensive ones (long cascades, heavy global
	 * propagators) defer between steps — see {@code Worklist} — so the driving
	 * scheduler interleaves other branches fairly. Granularity is the author's
	 * choice; the driver guarantees fairness only between fiber steps.
	 * Termination is the store's contraction obligation: updates may only shrink
	 * knowledge ({@code DomainUpdate} guarantees it for domains).
	 *
	 * @param prefix - exactly the newly applied bindings
	 * @param state - the extended live package to verify and read domains against
	 */
	Fiber<Revision> normalize(Theory<S> theory, Prefix prefix, Package state);

	/**
	 * Commit this store's constraints before {@code x} is reified: finite domains
	 * label their variables to ground values, projections fail on anything still
	 * unrun, disequalities have nothing to force. Runs once per answer, at the end
	 * of the search — not during propagation. (cKanren's enforce-constraints,
	 * Alvis et al.)
	 *
	 * @param x - the variable about to be reified
	 */
	<T> Goal enforce(Term<T> x);

	/**
	 * Render this store's residual constraints into the reified answer: after the
	 * answer term is renamed, each store attaches whatever still constrains it —
	 * disequality its surviving records, finite domains nothing (enforce grounded
	 * them). (cKanren's reify-constraints, Alvis et al.)
	 *
	 * @param unifiable - the reified answer built so far
	 * @param renaming - the crossing into the answer namespace
	 */
	<A> Term<A> reify(Theory<S> theory, Term<A> unifiable, Renaming renaming, Package p);

}
