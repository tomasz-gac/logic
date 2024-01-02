package com.tgac.logic.cKanren.parameters;
import com.tgac.logic.Goal;
import com.tgac.logic.Unifiable;
public interface EnforceConstraints {
	Goal enforce(Unifiable<?> x);
}
