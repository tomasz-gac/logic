package com.tgac.logic.projection;

import com.tgac.functional.Exceptions;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.Propagation;
import com.tgac.logic.ckanren.propagator.Propagator;
import com.tgac.logic.ckanren.propagator.Verdict;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
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
		return Propagation.changed(x)
				.and(s1 -> s1.getStore(ProjectionConstraints.class)
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
	public Revision changed(Term<?> x, Package state) {
		return administer(
				projections.filter(p -> p.watches(state, x)),
				state);
	}

	@Override
	public Revision stated(Stored item, Package state) {
		return item instanceof Propagator ?
				administer(LinkedHashSet.of((Propagator) item), state) :
				Revision.unchanged();
	}

	/**
	 * Projection verdicts are keep (not ground yet) or run (ground: unpark and
	 * splice the projected goal after quiescence); fail and subsumed are honored
	 * for completeness.
	 */
	private Revision administer(Iterable<Propagator> candidates, Package state) {
		@SuppressWarnings("unchecked")
		LinkedHashSet<Propagator>[] remaining = new LinkedHashSet[] {projections};
		java.util.List<Goal> runs = new java.util.ArrayList<>();
		for (Propagator p : candidates) {
			if (!remaining[0].contains(p)) {
				continue;
			}
			boolean dead = p.propagate(state).match(
					() -> true,
					() -> false,
					() -> {
						remaining[0] = remaining[0].remove(p);
						return false;
					},
					f -> {
						throw new UnsupportedOperationException(
								"projection propagators do not update their factor");
					},
					goal -> {
						remaining[0] = remaining[0].remove(p);
						runs.add(goal);
						return false;
					});
			if (dead) {
				return Revision.fail();
			}
		}
		if (remaining[0] == projections && runs.isEmpty()) {
			return Revision.unchanged();
		}
		Revision.Updated result = Revision.updated(new ProjectionConstraints(remaining[0]));
		for (Goal run : runs) {
			result = result.withRun(run);
		}
		return result;
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
		// hand the projected goal to the store's revision, which splices it after
		// the pass quiesces (docs/design/suspensions.md §5)
		return s -> Propagation.activate(
						Propagator.of(ProjectionConstraints.class,
								Collections.singletonList(x),
								state -> MiniKanren.walkAll(state, x).get()
										.asVal()
										.map(v -> Verdict.run(f.apply(v)))
										.getOrElse(Verdict::keep)))
				.apply(s.withStore(EMPTY));
	}
}
