package com.tgac.logic.cKanren.parameters;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import io.vavr.control.Try;
public interface ReifyConstraints {
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
	<A> Try<Unifiable<A>> reify(Unifiable<A> unifiable, Package renameSubstitutions, Package p);
}
