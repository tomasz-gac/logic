package com.tgac.logic.cKanren;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import io.vavr.collection.Array;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class Constraint implements PackageAccessor {
	PackageAccessor constraintOp;
	Array<Unifiable<?>> args;

	@Override
	public MRecur<Package> apply(Package p) {
		return constraintOp.apply(p);
	}

	public static Constraint buildOc(PackageAccessor constraintOp, Array<Unifiable<?>> args) {
		return Constraint.of(constraintOp, args);
	}
}
