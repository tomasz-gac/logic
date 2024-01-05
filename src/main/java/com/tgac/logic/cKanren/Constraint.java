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
	PackageAccessor constraintOp;
	Array<Unifiable<?>> args;

	@Override
	public Option<Package> apply(Package p) {
		return constraintOp.apply(p);
	}

	public static <T> Constraint buildOc(PackageAccessor constraintOp, Array<Unifiable<T>> args) {
		return Constraint.of(constraintOp, args.map(Unifiable::getObjectUnifiable));
	}
}
