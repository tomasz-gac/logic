package com.tgac.logic.projection;

import com.tgac.functional.Exceptions;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.recursion.Recur;
import com.tgac.functional.transformer.OptionT;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.ckanren.Constraint;
import com.tgac.logic.ckanren.ConstraintStore;
import com.tgac.logic.ckanren.StoreSupport;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class ProjectionConstraints implements ConstraintStore {
	private static final ProjectionConstraints EMPTY = new ProjectionConstraints(LinkedHashSet.empty());

	LinkedHashSet<Constraint> projections;

	@Override
	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return CKanren.runConstraints(x, projections)
				.and(s1 -> StoreSupport.getConstraintStore(s1, ProjectionConstraints.class)
						.projections
						.isEmpty() ?
						Cont.just(s1) :
						Exceptions.throwNow(new RuntimeException("Unbound variables during projection")));
	}

	@Override
	public Goal processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return s -> MiniKanren.prefixS(s.getSubstitutions(), newSubstitutions)
				.toJavaStream()
				.map(sub -> sub.apply((x, v) ->
						CKanren.runConstraints(x, projections)))
				.reduce(Goal.success(), Goal::and)
				.apply(s.withSubstitutions(newSubstitutions));
	}

	@Override
	public <A> Unifiable<A> reify(Unifiable<A> unifiable, Package renameSubstitutions, Package p) {
		return unifiable;
	}

	@Override
	public Store remove(Stored c) {
		if (c instanceof Constraint) {
			return new ProjectionConstraints(projections.remove((Constraint) c));
		} else {
			return this;
		}
	}

	@Override
	public Store prepend(Stored c) {
		if (c instanceof Constraint) {
			return new ProjectionConstraints(projections.add((Constraint) c));
		} else {
			return this;
		}
	}

	@Override
	public boolean contains(Stored c) {
		if (c instanceof Constraint) {
			return projections.contains((Constraint) c);
		} else {
			return false;
		}
	}

	public static <T> Goal project(Unifiable<T> x, Function<T, Goal> f) {
		return Goal.goal(s -> Cont.just(s.withStore(EMPTY)))
				.and(Goal.goal(s -> Cont.defer(() -> getVal(s, x)
						.map(f)
						.map(g -> g.apply(s))
						.getValue()
						// Add projection to CS
						.map(o -> o.orElseGet(() -> Cont.just(
								CKanren.buildWalkedConstraint(
										s1 -> project(x, f).apply(s1),
										Array.of(x),
										ProjectionConstraints.class,
										s).addTo(s))))
						.cast())));
	}

	private static <T> OptionT<Recur<?>, T> getVal(Package s, Unifiable<T> x) {
		return OptionT.just(MiniKanren.walkAll(s, x))
				.flatMap(u -> u.asVal()
						.map(v -> OptionT.just(Recur.done(v)))
						.getOrElse(OptionT.none(Recur::done)));
	}
}
