package com.tgac.logic.separate;

import static com.tgac.logic.separate.Disequality.purify;
import static com.tgac.logic.separate.Disequality.removeSubsumed;
import static com.tgac.logic.separate.Disequality.walkAllConstraints;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraints implements Projectable<NeqConstraints> {
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

	/** Record containment directly — the order the union-meet derives. */
	@Override
	public boolean leq(NeqConstraints other) {
		return constraints.containsAll(other.constraints);
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

	/**
	 * Lossless factoring: a record goes to the covered half iff every var it
	 * touches (LHS names and RHS term vars alike) is supplied.
	 * {@code _1 ∧ _2 = this}.
	 */
	@Override
	public Tuple2<NeqConstraints, NeqConstraints> split(java.util.List<LVar<?>> vars) {
		Set<Term<?>> covered = new java.util.HashSet<>(vars);
		LinkedHashSet<NeqConstraint> in = LinkedHashSet.empty();
		LinkedHashSet<NeqConstraint> out = LinkedHashSet.empty();
		for (NeqConstraint record : constraints) {
			if (fits(record, covered)) {
				in = in.add(record);
			} else {
				out = out.add(record);
			}
		}
		return Tuple.of(NeqConstraints.of(in), NeqConstraints.of(out));
	}

	private static boolean fits(NeqConstraint record, Set<Term<?>> covered) {
		for (Tuple2<Term<?>, Term<?>> pair : record.getSeparate()) {
			if (!covered.contains(pair._1) || escapes(pair._2, covered)) {
				return false;
			}
		}
		return true;
	}

	/** Records with their names translated through the renaming — LHS names
	 * map like any other (live var ↔ canonical hole), RHS terms map deeply. */
	@Override
	public NeqConstraints rename(Renaming renaming) {
		return NeqConstraints.of(LinkedHashSet.ofAll(constraints.map(record -> {
			HashMap<Term<?>, Term<?>> renamed = HashMap.empty();
			for (Tuple2<Term<?>, Term<?>> pair : record.getSeparate()) {
				renamed = renamed.put(renaming.apply(pair._1), renaming.apply(pair._2));
			}
			return NeqConstraint.of(renamed);
		})));
	}

	/** Iterative structural scan — deep spines must not recurse. */
	private static boolean escapes(Term<?> t, Set<Term<?>> covered) {
		Deque<Term<?>> work = new ArrayDeque<>();
		work.push(t);
		while (!work.isEmpty()) {
			Term<?> current = work.pop();
			if (current.asVar().isDefined()) {
				if (!covered.contains(current)) {
					return true;
				}
			} else {
				MiniKanren.members(current).forEach(members -> members.forEach(work::push));
			}
		}
		return false;
	}

	/** Wholesale re-verification IS this store's normal form — records are
	 * re-checked and simplified against the state, violated records fail. */
	@Override
	public Fiber<Revision> normalize(Package state) {
		return Fiber.done(Disequality.verifyAndSimplify(constraints.toList(), state.substitution())
				.map(c -> (Revision) Revision.updated(NeqConstraints.of(LinkedHashSet.ofAll(c))))
				.getOrElse(Revision::fail));
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
		return normalize(state);
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
