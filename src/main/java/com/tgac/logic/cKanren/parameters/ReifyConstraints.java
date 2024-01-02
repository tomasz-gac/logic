package com.tgac.logic.cKanren.parameters;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;

import java.util.function.Function;
public interface ReifyConstraints {
	<A> Function<Package, Unifiable<A>> reify(
			Unifiable<A> unifiable,
			Package renameSubstitutions);
}
