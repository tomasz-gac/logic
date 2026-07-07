package com.tgac.logic.projection;

import com.tgac.functional.Exceptions;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.transformer.OptionT;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.ckanren.Propagator;
import com.tgac.logic.ckanren.Verdict;
import com.tgac.logic.ckanren.ConstraintStore;
import com.tgac.logic.ckanren.Revision;
import com.tgac.logic.ckanren.StoreSupport;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import java.util.Collections;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class ProjectionConstraints implements ConstraintStore {
	private static final ProjectionConstraints EMPTY = new ProjectionConstraints(LinkedHashSet.empty());

	LinkedHashSet<Propagator> projections;

	@Override
	public <T> Goal enforce(Term<T> x) {
		return StoreSupport.wake(x)
				.and(s1 -> StoreSupport.getConstraintStore(s1, ProjectionConstraints.class)
						.projections
						.isEmpty() ?
						Cont.just(s1) :
						Exceptions.throwNow(new RuntimeException("Unbound variables during projection")));
	}

	@Override
	public Revision revise(Prefix prefix, Package state) {
		// projections are woken by the chokepoint's cross-store wake
		return Revision.unchanged();
	}

	@Override
	public Iterable<Propagator> pendingPropagators() {
		return projections;
	}

	@Override
	public <A> Term<A> reify(Term<A> unifiable, Package renameSubstitutions, Package p) {
		return unifiable;
	}

	@Override
	public boolean isEmpty() {
		return projections.isEmpty();
	}

	@Override
	public Store remove(Stored c) {
		if (c instanceof Propagator) {
			return new ProjectionConstraints(projections.remove((Propagator) c));
		} else {
			return this;
		}
	}

	@Override
	public Store prepend(Stored c) {
		if (c instanceof Propagator) {
			return new ProjectionConstraints(projections.add((Propagator) c));
		} else {
			return this;
		}
	}

	@Override
	public boolean contains(Stored c) {
		if (c instanceof Propagator) {
			return projections.contains((Propagator) c);
		} else {
			return false;
		}
	}

	public static <T> Goal project(Unifiable<T> x, Function<T, Goal> f) {
		// parks a suspension-shaped propagator: keep until x is deep-ground, then
		// hand the projected goal to the driver, which splices it after the pass
		// quiesces (docs/design/suspensions.md §5)
		return s -> StoreSupport.activate(
				Propagator.of(ProjectionConstraints.class,
						Collections.singletonList(x),
						state -> MiniKanren.walkAll(state, x).get()
								.asVal()
								.map(v -> Verdict.run(f.apply(v)))
								.getOrElse(Verdict::keep)),
				s.withStore(EMPTY));
	}
}
