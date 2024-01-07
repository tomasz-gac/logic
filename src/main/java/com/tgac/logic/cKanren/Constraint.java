package com.tgac.logic.cKanren;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class Constraint implements PackageAccessor {
	Class<? extends ConstraintStore> tag;
	PackageAccessor constraintOp;
	Array<Unifiable<?>> args;

	@Override
	public Option<Package> apply(Package p) {
		return constraintOp.apply(p);
	}

	public static <T, S extends ConstraintStore> Constraint buildOc(Class<S> tag, PackageAccessor constraintOp, Array<Unifiable<T>> args) {
		return Constraint.of(tag, constraintOp, args.map(Unifiable::getObjectUnifiable));
	}
}
