package com.tgac.logic.ckanren;
import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.unification.Package;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Function;

/**
 * This is the λ_M/f_M from cKanren paper. It is a recursive function from Package ⟶ optional Package
 */
public interface PackageAccessor extends Function<Package, Option<Package>> {

	static PackageAccessor of(PackageAccessor op) {
		return op;
	}

	static PackageAccessor identity() {
		return Option::of;
	}

	static PackageAccessor constant(Package p) {
		return __ -> Option.of(p);
	}

	default ComposedPackageAccessor compose(PackageAccessor other) {
		return new ComposedPackageAccessor(List.of(other, this));
	}

	@Value
	@RequiredArgsConstructor
	class ComposedPackageAccessor implements PackageAccessor {
		List<PackageAccessor> ops;

		@Override
		public Option<Package> apply(Package aPackage) {
			return ops
					.reverse()
					.toJavaStream()
					.reduce(Option.of(aPackage),
							Option::flatMap,
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
		public Option<Package> apply(Package aPackage) {
			return rest.get().apply(aPackage);
		}
	}
}
