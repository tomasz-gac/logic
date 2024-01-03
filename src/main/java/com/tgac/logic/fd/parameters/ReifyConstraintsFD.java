package com.tgac.logic.fd.parameters;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.parameters.ReifyConstraints;

import java.util.function.Function;
public class ReifyConstraintsFD implements ReifyConstraints {
	@Override
	public <A> Function<Package, Unifiable<A>> reify(Unifiable<A> unifiable, Package renameSubstitutions) {
		throw new IllegalStateException("Unbound variables");
	}
}
