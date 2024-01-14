package com.tgac.logic.separate;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.ckanren.parameters.ConstraintStore;
import com.tgac.logic.unification.Constraint;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import static com.tgac.logic.separate.NeqGoals.purify;
import static com.tgac.logic.separate.NeqGoals.removeSubsumed;
import static com.tgac.logic.separate.NeqGoals.walkAllConstraints;

@Value
@RequiredArgsConstructor(staticName = "of")
public class NeqConstraints implements ConstraintStore {
	public static final NeqConstraints EMPTY = NeqConstraints.of(List.empty());
	List<NeqConstraint> constraints;

	private static ConstraintStore empty() {
		return EMPTY;
	}

	public static NeqConstraints get(Package p) {
		return (NeqConstraints) p.getConstraintStore();
	}
	public static List<NeqConstraint> getConstraints(Package p) {
		return get(p).getConstraints();
	}
	public static Package register(Package a) {
		return a.withConstraintStore(empty());
	}

	@Override
	public ConstraintStore remove(Constraint c) {
		return NeqConstraints.of(constraints.remove((NeqConstraint) c));
	}
	@Override
	public ConstraintStore prepend(Constraint c) {
		return NeqConstraints.of(constraints.prepend((NeqConstraint) c));
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
		return s -> NeqGoals.verifyUnify(s.withSubstitutions(newSubstitutions), s);
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
