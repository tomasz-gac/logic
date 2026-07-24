package com.tgac.logic.separate;

import static com.tgac.logic.separate.Disequality.purify;
import static com.tgac.logic.separate.Disequality.removeSubsumed;
import static com.tgac.logic.separate.Disequality.walkAllConstraints;

import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.ArrayDeque;
import java.util.Deque;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraints implements Projectable<NeqResidue>, MeetSemilattice<NeqConstraints> {
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

	/**
	 * TRANSCRIBES every record wholly over {@code vars}: LHS var → slot, RHS
	 * term with projected vars renamed to canonical holes — pure data, so
	 * same-shaped contexts from unrelated lineages project equal residues.
	 * {@code wideningAllowed} governs only the escapes (a record touching an
	 * unsupplied var): dropped by permission, refused when exactness demanded.
	 */
	@Override
	public NeqResidue project(java.util.List<LVar<?>> vars, boolean wideningAllowed) {
		HashMap<LVar<?>, Term<?>> renames = HashMap.empty();
		for (int i = 0; i < vars.size(); i++) {
			renames = renames.put(vars.get(i), Hole.of(i));
		}
		Substitutions rename = Substitutions.of(renames);
		HashSet<HashMap<Integer, Term<?>>> transcribed = HashSet.empty();
		for (NeqConstraint record : constraints) {
			HashMap<Integer, Term<?>> slots = transcribe(record, rename);
			if (slots != null) {
				transcribed = transcribed.add(slots);
			} else if (!wideningAllowed) {
				throw new IllegalStateException(
						"exact projection demanded but a disequality escapes the "
								+ "projected vars — ground the escaping var or include it");
			}
			// else: dropped by permission — the caller declared widening sound
		}
		return NeqResidue.of(transcribed);
	}

	/** Slot → renamed forbidden term, or null when any var escapes the projection. */
	private static HashMap<Integer, Term<?>> transcribe(NeqConstraint record, Substitutions rename) {
		HashMap<Integer, Term<?>> slots = HashMap.empty();
		for (Tuple2<LVar<?>, Term<?>> pair : record.getSeparate()) {
			Term<?> lhs = rename.walk(pair._1);
			if (!lhs.asReified().isDefined()) {
				return null;    // the constrained var itself is not projected
			}
			Term<?> rhs = MiniKanren.walkAll(rename, pair._2).get();
			if (hasVars(rhs)) {
				return null;    // the forbidden term reaches an unprojected var
			}
			slots = slots.put(((Hole<?>) lhs).getNumber(), rhs);
		}
		return slots;
	}

	/** Records with their vars translated through the renaming: LHS vars stay
	 * vars (the store invariant keeps them unbound), RHS terms map deeply. */
	@Override
	public NeqConstraints rename(Renaming renaming) {
		return NeqConstraints.of(LinkedHashSet.ofAll(constraints.map(record -> {
			HashMap<LVar<?>, Term<?>> renamed = HashMap.empty();
			for (Tuple2<LVar<?>, Term<?>> pair : record.getSeparate()) {
				renamed = renamed.put(
						(LVar<?>) renaming.apply(pair._1).asVar().get(),
						renaming.apply(pair._2));
			}
			return NeqConstraint.of(renamed);
		})));
	}

	@Override
	public Goal stated() {
		Goal all = Goal.success();
		for (NeqConstraint record : constraints) {
			all = Conjunction.of(all, statedRecord(record));
		}
		return all;
	}

	private static Goal statedRecord(NeqConstraint record) {
		java.util.List<Object> lhs = new java.util.ArrayList<>();
		java.util.List<Object> rhs = new java.util.ArrayList<>();
		for (Tuple2<LVar<?>, Term<?>> pair : record.getSeparate()) {
			lhs.add(pair._1);
			rhs.add(pair._2);
		}
		return Disequality.<java.util.List<Object>> separate(LVal.lval(lhs), LVal.lval(rhs));
	}

	/** Iterative structural scan — deep spines must not recurse. */
	private static boolean hasVars(Term<?> t) {
		Deque<Term<?>> work = new ArrayDeque<>();
		work.push(t);
		while (!work.isEmpty()) {
			Term<?> current = work.pop();
			if (current.asVar().isDefined()) {
				return true;
			}
			MiniKanren.members(current).forEach(members -> members.forEach(work::push));
		}
		return false;
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
