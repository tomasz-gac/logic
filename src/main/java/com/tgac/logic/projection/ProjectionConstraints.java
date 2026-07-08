package com.tgac.logic.projection;

// ABOUTME: Suspensions as a store: a projection parks a goal-producing body until its
// ABOUTME: watched term is deep-ground, then hands the goal to the run lane.

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.Propagation;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.ckanren.store.Revision;
import com.tgac.logic.ckanren.store.Watches;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class ProjectionConstraints implements ConstraintStore {
	private static final ProjectionConstraints EMPTY = new ProjectionConstraints(LinkedHashSet.empty());

	LinkedHashSet<Projection> projections;

	/**
	 * A parked suspension: nothing but the watched term and the body to run once
	 * it is deep-ground. No verdicts, no propagator machinery — groundness is the
	 * only question a projection ever asks.
	 */
	@Value
	static class Projection implements Stored {
		Term<?> target;
		Function<Object, Goal> body;

		@Override
		public Class<? extends Store> getStoreClass() {
			return ProjectionConstraints.class;
		}

		@Override
		public String toString() {
			return "project(" + target + ")";
		}
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		// self-service at search position: run whatever projections of x are
		// ground, then anything still parked is a programming error
		return s -> {
			ProjectionConstraints self = s.getStore(ProjectionConstraints.class);
			return self.examine(self.projections.filter(p -> Watches.matches(s, p.target, x)), s)
					.<Cont<Package, Nothing>> match(
							() -> Cont.complete(Nothing.nothing()),
							() -> verifyDrained(s),
							upd -> {
								Package cleared = s.putStore(upd.factor());
								Goal drained = s2 -> verifyDrained(s2);
								return upd.runs().stream()
										.reduce(Goal.success(), Goal::and)
										.and(drained)
										.apply(cleared);
							});
		};
	}

	private static Cont<Package, Nothing> verifyDrained(Package s) {
		return s.getStore(ProjectionConstraints.class).projections.isEmpty() ?
				Cont.just(s) :
				Exceptions.throwNow(new RuntimeException("Unbound variables during projection"));
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// a projection only cares about groundness, so its own watchers of the
		// newly bound variables are re-examined right here
		LinkedHashSet<Projection> candidates = LinkedHashSet.empty();
		for (Tuple2<LVar<?>, Term<?>> binding : prefix.bindings()) {
			candidates = candidates.addAll(
					projections.filter(p -> Watches.matches(state, p.target, binding._1)));
		}
		return Fiber.done(examine(candidates, state));
	}

	@Override
	public Fiber<Revision> stated(Stored item, Package state) {
		return Fiber.done(item instanceof Projection ?
				examine(LinkedHashSet.of((Projection) item), state) :
				Revision.unchanged());
	}

	/** Ground candidates unpark and hand their goals to the run lane; the rest wait. */
	@SuppressWarnings("unchecked")
	private Revision examine(Iterable<Projection> candidates, Package state) {
		LinkedHashSet<Projection> remaining = projections;
		java.util.List<Goal> runs = new java.util.ArrayList<>();
		for (Projection p : candidates) {
			if (!remaining.contains(p)) {
				continue;
			}
			Term<Object> walked = MiniKanren.walkAll(state, (Term<Object>) p.getTarget()).get();
			if (walked.asVal().isDefined()) {
				remaining = remaining.remove(p);
				runs.add(p.getBody().apply(walked.get()));
			}
		}
		if (remaining == projections && runs.isEmpty()) {
			return Revision.unchanged();
		}
		Revision.Updated result = Revision.updated(new ProjectionConstraints(remaining));
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
		if (c instanceof Projection) {
			return new ProjectionConstraints(projections.remove((Projection) c));
		} else {
			return this;
		}
	}

	@Override
	public Store prepend(Stored c) {
		if (c instanceof Projection) {
			return new ProjectionConstraints(projections.add((Projection) c));
		} else {
			return this;
		}
	}

	@Override
	public boolean contains(Stored c) {
		if (c instanceof Projection) {
			return projections.contains((Projection) c);
		} else {
			return false;
		}
	}

	/**
	 * Parks a suspension: keep until {@code x} is deep-ground, then the body's
	 * goal joins the run lane and splices after the pass quiesces
	 * (docs/design/suspensions.md §5).
	 */
	@SuppressWarnings("unchecked")
	public static <T> Goal project(Unifiable<T> x, Function<T, Goal> f) {
		return s -> Propagation.activate(
						new Projection(x, v -> f.apply((T) v)))
				.apply(s.withStore(EMPTY));
	}
}
