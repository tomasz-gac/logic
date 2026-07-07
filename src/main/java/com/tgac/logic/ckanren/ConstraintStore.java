package com.tgac.logic.ckanren;
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
	 * <pre>
	 *    This function is run immediately before we reify the constraints
	 *    and should accept the variable to be reified. Any checks for consistency
	 *    or reorganization of the constraint store can be done here.
	 *
	 *    cKanren, miniKanren with Constraints. Alvis et al.
	 * </pre>
	 *
	 * @param x
	 * 		- variable to enforce constraints against
	 */
	<T> Goal enforceConstraints(Term<T> x);

	/**
	 * React to newly applied bindings (cKanren's process-prefix, capability form:
	 * docs/design/capability-constraint-api.md §2.3). The chokepoint has already
	 * applied the extension; the store may read anything and change only its own
	 * factor — a whole package is not expressible in the return type.
	 *
	 * @param prefix
	 * 		- exactly the newly applied bindings
	 * @param state
	 * 		- the extended live package to verify and read domains against
	 */
	Reaction onPrefix(Prefix prefix, Package state);

	/**
	 * The suspended {@link Propagator}s this store holds, exposed for the
	 * chokepoint's cross-store wake: when a variable is bound or narrowed, every
	 * store's propagators watching it are re-run, not only the store that caused
	 * the change. Stores using wholesale verification (disequality) have none —
	 * they participate through {@link #onPrefix} instead.
	 */
	default Iterable<Propagator> pendingPropagators() {
		return Collections.emptyList();
	}

	/**
	 * <pre>
	 *    This function is run as part of the reifier.
	 *    It is responsible for building a Scheme data structure
	 *    that represents the information in the constraint store of a package.
	 *
	 *    cKanren, miniKanren with Constraints. Alvis et al.
	 * </pre>
	 *
	 * @param unifiable
	 * 		- the variable to unify
	 * @param renameSubstitutions
	 * 		- substitutions used in variable renaming
	 */
	<A> Term<A> reify(Term<A> unifiable, Package renameSubstitutions, Package p);

}
