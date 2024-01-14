package com.tgac.logic.ckanren.parameters;
import com.tgac.logic.unification.Stored;

public interface Store extends EnforceConstraints, ReifyConstraints, ProcessPrefix {
	Store remove(Stored c);

	Store prepend(Stored c);

	boolean contains(Stored c);
}
