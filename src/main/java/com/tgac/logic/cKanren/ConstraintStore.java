package com.tgac.logic.cKanren;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.parameters.EnforceConstraints;
import com.tgac.logic.cKanren.parameters.ReifyConstraints;
import io.vavr.collection.HashMap;
public interface ConstraintStore extends EnforceConstraints, ReifyConstraints {
	ConstraintStore remove(Constraint c);

	ConstraintStore prepend(Constraint c);

	boolean contains(Constraint c);

	PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> prefix);
}
