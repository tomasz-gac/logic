package com.tgac.logic.ckanren.parameters;
import com.tgac.logic.Goal;
import com.tgac.logic.unification.Unifiable;

public interface EnforceConstraints {
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
}
