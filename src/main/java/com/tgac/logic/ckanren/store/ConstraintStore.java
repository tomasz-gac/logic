package com.tgac.logic.ckanren.store;
import com.tgac.logic.ckanren.propagator.Propagator;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import java.util.Collections;

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
	 * @param x
	 * 		- the variable about to be reified
	 */
	<T> Goal enforce(Term<T> x);

	/**
	 * Revise this store against newly applied bindings — AC-3's REVISE, cKanren's
	 * process-prefix (capability form: docs/design/capability-constraint-api.md
	 * §2.3). The chokepoint has already applied the extension; the store may read
	 * anything and change only its own factor — a whole package is not
	 * expressible in the return type.
	 *
	 * @param prefix
	 * 		- exactly the newly applied bindings
	 * @param state
	 * 		- the extended live package to verify and read domains against
	 */
	Revision revise(Prefix prefix, Package state);

	/**
	 * The suspended {@link Propagator}s this store holds, exposed for the
	 * chokepoint's cross-store wake: when a variable is bound or narrowed, every
	 * store's propagators watching it are re-run, not only the store that caused
	 * the change. Stores using wholesale verification (disequality) have none —
	 * they participate through {@link #revise} instead.
	 */
	default Iterable<Propagator> pendingPropagators() {
		return Collections.emptyList();
	}

	/**
	 * Render this store's residual constraints into the reified answer: after the
	 * answer term is renamed, each store attaches whatever still constrains it —
	 * disequality its surviving records, finite domains nothing (enforce grounded
	 * them). (cKanren's reify-constraints, Alvis et al.)
	 *
	 * @param unifiable
	 * 		- the reified answer built so far
	 * @param renameSubstitutions
	 * 		- substitutions used in variable renaming
	 */
	<A> Term<A> reify(Term<A> unifiable, Package renameSubstitutions, Package p);

}
