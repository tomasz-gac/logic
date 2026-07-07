package com.tgac.logic.separate;

import static com.tgac.logic.separate.Disequality.purify;
import static com.tgac.logic.separate.Disequality.removeSubsumed;
import static com.tgac.logic.separate.Disequality.walkAllConstraints;

import com.tgac.logic.ckanren.ConstraintStore;
import com.tgac.logic.ckanren.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraints implements ConstraintStore {
	public static final NeqConstraints EMPTY = NeqConstraints.of(List.empty());
	List<NeqConstraint> constraints;

	private static ConstraintStore empty() {
		return EMPTY;
	}

	public static NeqConstraints get(Package p) {
		return p.getStore(NeqConstraints.class);
	}

	public static List<NeqConstraint> getConstraints(Package p) {
		return get(p).getConstraints();
	}

	public static Package register(Package a) {
		return a.withStore(empty());
	}

	@Override
	public boolean isEmpty() {
		return constraints.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return NeqConstraints.of(constraints.remove((NeqConstraint) c));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return NeqConstraints.of(constraints.prepend((NeqConstraint) c));
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof NeqConstraint
				&& constraints.contains((NeqConstraint) c);
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	@Override
	public Revision revise(Prefix prefix, Package state) {
		return Disequality.verifyAndSimplify(constraints, state.getSubstitutions())
				.map(c -> (Revision) Revision.updated(NeqConstraints.of(c)))
				.getOrElse(Revision::fail);
	}

	@Override
	public <A> Term<A> reify(Term<A> unifiable, Package renamePackage, Package s) {
		return walkAllConstraints(getConstraints(s), s)
				.flatMap(c_star -> removeSubsumed(
						purify(c_star, renamePackage),
						List.empty())
						.flatMap(c1 -> Disequality.renameForDisplay(c1, renamePackage)))
				.map(c1 -> c1.isEmpty() ?
						unifiable :
						Constrained.of(unifiable, c1))
				.get();
	}
}
