package com.tgac.logic.ckanren;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;

public interface ConstraintStore extends Store {
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
	<T> Goal enforceConstraints(Unifiable<T> x);

	/**
	 * <pre>
	 * 	This function is sent a prefix of the substitution, consisting of
	 * 	all the associations newly added after a unification. In addition, it is sent
	 * 	the current constraints. This can be an opportunity to rerun constraints
	 * 	for the variables with new associations but different constraints.
	 *
	 * 	cKanren, miniKanren with Constraints. Alvis et al.
	 * </pre>
	 *
	 * Old package to which prefix is added is passed to PackageAccessor
	 *
	 * @param newSubstitutions
	 * 		- Newly added substitutions
	 */
	PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions);

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
	<A> Unifiable<A> reify(Unifiable<A> unifiable, Package renameSubstitutions, Package p);

}
