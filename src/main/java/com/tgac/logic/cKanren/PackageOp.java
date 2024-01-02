package com.tgac.logic.cKanren;
import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.MRecur;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.Package;
import io.vavr.collection.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Function;

public interface PackageOp extends Function<Package, MRecur<Package>> {

	static PackageOp of(PackageOp op) {
		return op;
	}

	static PackageOp identity() {
		return MRecur::mdone;
	}

	default ComposedPackageOp compose(PackageOp other) {
		return new ComposedPackageOp(List.of(other, this));
	}

	@Value
	@RequiredArgsConstructor
	class ComposedPackageOp implements PackageOp {
		List<PackageOp> ops;

		@Override
		public MRecur<Package> apply(Package aPackage) {
			return ops
					.reverse()
					.toJavaStream()
					.reduce(MRecur.mdone(aPackage),
							MRecur::flatMap,
							Exceptions.throwingBiOp(UnsupportedOperationException::new));
		}

		public ComposedPackageOp compose(PackageOp other) {
			return new ComposedPackageOp(ops.prepend(other));
		}
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	class Incomplete implements PackageOp {
		Recur<PackageOp> rest;

		@Override
		public MRecur<Package> apply(Package aPackage) {
			return rest.get().apply(aPackage);
		}
	}
}
