package com.tgac.logic.separate;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.ckanren.parameters.ConstraintStore;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import static com.tgac.logic.separate.SeparateGoals.purify;
import static com.tgac.logic.separate.SeparateGoals.removeSubsumed;
import static com.tgac.logic.separate.SeparateGoals.walkAllConstraints;

@Value
@RequiredArgsConstructor(staticName = "of")
public class SeparatenessConstraints implements ConstraintStore {
	List<NeqConstraint> constraints;

	public static void use() {
		Package.register(SeparatenessConstraints.empty());
	}
	private static ConstraintStore empty() {
		return SeparatenessConstraints.of(List.empty());
	}

	public static SeparatenessConstraints get(Package p) {
		return p.get(SeparatenessConstraints.class);
	}
	public static List<NeqConstraint> getConstraints(Package p) {
		return get(p).getConstraints();
	}

	@Override
	public ConstraintStore remove(Constraint c) {
		return SeparatenessConstraints.of(constraints.remove((NeqConstraint) c));
	}
	@Override
	public ConstraintStore prepend(Constraint c) {
		return SeparatenessConstraints.of(constraints.prepend((NeqConstraint) c));
	}
	@Override
	public boolean contains(Constraint c) {
		return c instanceof NeqConstraint
				&& constraints.contains((NeqConstraint) c);
	}
	@Override
	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return Goal.success();
	}
	@Override
	public PackageAccessor processPrefix(
			HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return s -> SeparateGoals.verifyUnify(s.withSubstitutions(newSubstitutions), s);
	}

	@Override
	public <A> Try<Unifiable<A>> reify(Unifiable<A> unifiable, Package renamePackage, Package s) {
		return walkAllConstraints(getConstraints(s), s)
				.flatMap(c_star -> removeSubsumed(
						purify(c_star, renamePackage),
						List.empty())
						.flatMap(c1 -> walkAllConstraints(c1, renamePackage)))
				.map(c1 -> c1.isEmpty() ?
						unifiable :
						Constrained.of(unifiable, c1.map(NeqConstraint::getSeparate)))
				.map(Try::success)
				.get();
	}
}
