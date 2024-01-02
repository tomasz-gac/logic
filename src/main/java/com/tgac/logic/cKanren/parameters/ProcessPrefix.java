package com.tgac.logic.cKanren.parameters;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageOp;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
public interface ProcessPrefix {
	MRecur<PackageOp> process(
			HashMap<LVar<?>, Unifiable<?>> prefix,
			List<Constraint> constraints);
}
