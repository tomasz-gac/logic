package com.tgac.logic.ckanren;
import com.tgac.logic.ckanren.parameters.ConstraintStore;

public interface Constraint extends PackageAccessor {
	Class<? extends ConstraintStore> getTag();
}
