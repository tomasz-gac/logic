package com.tgac.logic.cKanren;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import io.vavr.collection.Array;

import java.util.stream.Collectors;
public interface Constraint extends PackageOp {
	Array<Unifiable<?>> getArgs();

	static Constraint buildOc(PackageOp packageOp, Array<Unifiable<?>> args) {
		return new Constraint() {
			@Override
			public Array<Unifiable<?>> getArgs() {
				return args;
			}
			@Override
			public MRecur<Package> apply(Package aPackage) {
				return packageOp.apply(aPackage);
			}
			@Override
			public String toString() {
				return packageOp.toString() + "(" + args.map(Object::toString).collect(Collectors.joining(",")) + ")";
			}
		};
	}
}
