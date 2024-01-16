package com.tgac.logic.ckanren;
import com.tgac.logic.Goal;
import com.tgac.logic.step.Step;
import com.tgac.logic.unification.Package;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.var;

import java.util.ArrayList;
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

	static PackageAccessor failure() {
		return s -> Option.none();
	}

	default ComposedPackageAccessor compose(PackageAccessor other) {
		return new ComposedPackageAccessor()
				.compose(this)
				.compose(other);
	}

	default Goal asGoal() {
		return s -> Step.of(apply(s));
	}

	@Value
	@RequiredArgsConstructor
	class ComposedPackageAccessor implements PackageAccessor {
		ArrayList<PackageAccessor> ops = new ArrayList<>();

		@Override
		public Option<Package> apply(Package aPackage) {
			Option<Package> result = Option.of(aPackage);
			for (var op : ops) {
				result = op.apply(result.get());
				if (result.isEmpty()) {
					return result;
				}
			}
			return result;
		}

		public ComposedPackageAccessor compose(PackageAccessor other) {
			ops.add(other);
			return this;
		}
	}
}
