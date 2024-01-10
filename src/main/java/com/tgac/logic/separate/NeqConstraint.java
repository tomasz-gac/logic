package com.tgac.logic.separate;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.parameters.ConstraintStore;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class NeqConstraint implements Constraint {
	HashMap<LVar<?>, Unifiable<?>> separate;

	@Override
	public Option<Package> apply(Package aPackage) {
		return null;
	}
	@Override
	public Class<? extends ConstraintStore> getTag() {
		return SeparatenessConstraints.class;
	}
}
