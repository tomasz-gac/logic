package com.tgac.logic.ckanren.parameters;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;

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
	 * Old package to which prefix is added is passed to PackageAccessor
	 *
	 * @param newSubstitutions
	 * 		- Newly added substitutions
	 */
	PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions);
}
