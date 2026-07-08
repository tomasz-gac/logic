package com.tgac.logic.ckanren.store;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;

public interface ConstraintStore extends Store {

	/**
	 * Whether the store currently holds no constraints.
	 */
	boolean isEmpty();

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
	 * Revise this store against newly applied bindings — AC-3's REVISE, cKanren's
	 * process-prefix. The chokepoint has already applied the extension; the store
	 * may read anything and change only its own factor — a whole package is not
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
	 * @param prefix
	 * 		- exactly the newly applied bindings
	 * @param state
	 * 		- the extended live package to verify and read domains against
	 */
	Fiber<Revision> revise(Prefix prefix, Package state);

	/**
	 * One of this store's items was just stated ({@code Propagation.activate}
	 * parked it already). First examination: a constraint over already-ground
	 * terms will never be woken, so whatever can be decided or narrowed at
	 * statement time must be decided here. Same scheduling contract as
	 * {@link #revise}.
	 */
	default Fiber<Revision> stated(Stored item, Package state) {
		return Fiber.done(Revision.unchanged());
	}

	/**
	 * Render this store's residual constraints into the reified answer: after the
	 * answer term is renamed, each store attaches whatever still constrains it —
	 * disequality its surviving records, finite domains nothing (enforce grounded
	 * them). (cKanren's reify-constraints, Alvis et al.)
	 *
	 * @param unifiable - the reified answer built so far
	 * @param renameSubstitutions - substitutions used in variable renaming
	 */
	<A> Term<A> reify(Term<A> unifiable, Package renameSubstitutions, Package p);

}
