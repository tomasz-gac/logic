package com.tgac.logic.cKanren;
import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.MRecur;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.Package;
import io.vavr.collection.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Function;

/**
 * This is the λ_M/f_M from cKanren paper. It is a recursive function from Package ⟶ optional Package
 */
public interface PackageAccessor extends Function<Package, MRecur<Package>> {

	static PackageAccessor of(PackageAccessor op) {
		return op;
	}

	static PackageAccessor identity() {
		return MRecur::mdone;
	}

	default ComposedPackageAccessor compose(PackageAccessor other) {
		return new ComposedPackageAccessor(List.of(other, this));
	}

	@Value
	@RequiredArgsConstructor
	class ComposedPackageAccessor implements PackageAccessor {
		List<PackageAccessor> ops;

		@Override
		public MRecur<Package> apply(Package aPackage) {
			return ops
					.reverse()
					.toJavaStream()
					.reduce(MRecur.mdone(aPackage),
							MRecur::flatMap,
							Exceptions.throwingBiOp(UnsupportedOperationException::new));
		}

		public ComposedPackageAccessor compose(PackageAccessor other) {
			return new ComposedPackageAccessor(ops.prepend(other));
		}
	}

	@Value
	@RequiredArgsConstructor(staticName = "of")
	class Incomplete implements PackageAccessor {
		Recur<PackageAccessor> rest;

		@Override
		public MRecur<Package> apply(Package aPackage) {
			return rest.get().apply(aPackage);
		}
	}
}
