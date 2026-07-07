package com.tgac.logic.ckanren.store;

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
	 * process-prefix (capability form: docs/design/capability-constraint-api.md
	 * §2.3). The chokepoint has already applied the extension; the store may read
	 * anything and change only its own factor — a whole package is not
	 * expressible in the return type.
	 *
	 * @param prefix - exactly the newly applied bindings
	 * @param state - the extended live package to verify and read domains against
	 */
	Revision revise(Prefix prefix, Package state);

	/**
	 * A term changed — bound or narrowed. Re-examine whatever this store has
	 * watching it; the trigger broadcasts to every store, so cross-store waking
	 * needs no other machinery. Stores with nothing parked (disequality) keep the
	 * default — they participate through {@link #revise} instead.
	 */
	default Revision changed(Term<?> x, Package state) {
		return Revision.unchanged();
	}

	/**
	 * One of this store's items was just stated ({@code Propagation.activate}
	 * parked it already). First examination: a constraint over already-ground
	 * terms will never be woken, so whatever can be decided or narrowed at
	 * statement time must be decided here.
	 */
	default Revision stated(Stored item, Package state) {
		return Revision.unchanged();
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
