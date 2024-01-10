package com.tgac.logic.ckanren.parameters;
import com.tgac.logic.ckanren.Constraint;

public interface ConstraintStore extends EnforceConstraints, ReifyConstraints, ProcessPrefix {
	ConstraintStore remove(Constraint c);

	ConstraintStore prepend(Constraint c);

	boolean contains(Constraint c);
}
