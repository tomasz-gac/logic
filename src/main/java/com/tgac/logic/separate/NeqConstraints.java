package com.tgac.logic.separate;

import static com.tgac.logic.separate.Disequality.purify;
import static com.tgac.logic.separate.Disequality.removeSubsumed;
import static com.tgac.logic.separate.Disequality.walkAllConstraints;

import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraints implements ConstraintStore, MeetSemilattice<NeqConstraints> {
	public static final NeqConstraints EMPTY = NeqConstraints.of(LinkedHashSet.empty());
	LinkedHashSet<NeqConstraint> constraints;

	private static ConstraintStore empty() {
		return EMPTY;
	}

	public static NeqConstraints get(Package p) {
		return p.getStore(NeqConstraints.class);
	}

	public static List<NeqConstraint> getConstraints(Package p) {
		// newest-first, the iteration order reify has always rendered
		return get(p).getConstraints().toList().reverse();
	}

	/**
	 * More records = more known: meet is record union, so the derived leq is
	 * record superset. The store is a set semantically — revise re-verifies
	 * wholesale and subsumption prunes — so union is exact, not a widening.
	 */
	@Override
	public NeqConstraints meet(NeqConstraints other) {
		return NeqConstraints.of(constraints.addAll(other.constraints));
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
		return NeqConstraints.of(constraints.add((NeqConstraint) c));
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
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		return Fiber.done(Disequality.verifyAndSimplify(constraints.toList(), state.substitution())
				.map(c -> (Revision) Revision.updated(NeqConstraints.of(LinkedHashSet.ofAll(c))))
				.getOrElse(Revision::fail));
	}

	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package s) {
		return walkAllConstraints(getConstraints(s), s.substitution())
				.flatMap(c_star -> removeSubsumed(
						purify(c_star, renameSubstitutions),
						List.empty())
						.flatMap(c1 -> Disequality.renameForDisplay(c1, renameSubstitutions)))
				.map(c1 -> c1.isEmpty() ?
						unifiable :
						Constrained.of(unifiable, c1))
				.get();
	}
}
