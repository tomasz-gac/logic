package com.tgac.logic.ckanren;

import static com.tgac.functional.category.Nothing.nothing;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.var;

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

	static PackageAccessor failure() {
		return a -> Option.none();
	}

	static PackageAccessor filter(boolean pass) {
		return pass ? identity() : failure();
	}

	default ComposedPackageAccessor compose(PackageAccessor other) {
		return new ComposedPackageAccessor()
				.compose(this)
				.compose(other);
	}

	default Goal asGoal() {
		return s -> apply(s)
				.map(Cont::<Package, Nothing>just)
				.getOrElse(() -> Cont.complete(nothing()));
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
