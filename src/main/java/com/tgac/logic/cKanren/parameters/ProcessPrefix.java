package com.tgac.logic.cKanren.parameters;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageAccessor;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

public interface ProcessPrefix {

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
	 * @param prefix
	 * 		- Newly added substitutions
	 * @param constraints
	 * 		- current constraints
	 */
	MRecur<PackageAccessor> processPrefix(
			HashMap<LVar<?>, Unifiable<?>> prefix,
			List<Constraint> constraints);
}
