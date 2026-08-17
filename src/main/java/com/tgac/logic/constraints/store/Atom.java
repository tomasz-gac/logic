package com.tgac.logic.constraints.store;

import com.tgac.logic.goals.Store;
import com.tgac.logic.unification.Term;
import java.util.stream.Stream;

public interface Atom<S extends Constraint<S>> extends Constraint<S> {
	Class<? extends Constraint<S>> getConstraintClass();

	String name();
	Stream<Term<?>> watched();
	Object payload();
}
