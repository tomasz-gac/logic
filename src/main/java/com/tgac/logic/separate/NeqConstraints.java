package com.tgac.logic.separate;

// ABOUTME: The Prefix-cargo instance of the note chassis: a record's escapes are
// ABOUTME: its pairs, verified jointly by trial unification — Neq as notes.

import static com.tgac.logic.separate.Disequality.purify;
import static com.tgac.logic.separate.Disequality.removeSubsumed;
import static com.tgac.logic.separate.Disequality.walkAllConstraints;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Package;
import com.tgac.logic.notes.Notes;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A record is one note: "at least one pair must stay apart." The escapes are
 * verified JOINTLY — trial unification threads bindings across pairs that
 * share variables — so the cargo's verifier is the whole-record trial, not a
 * per-escape loop: satisfied records discard, violated records fail, the
 * surviving delta IS the simplified record (impossible pairs dropped out).
 */
@Getter
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor(staticName = "of")
final class NeqConstraints extends Notes<NeqConstraint, NeqConstraints> {
	public static final NeqConstraints EMPTY = NeqConstraints.of(LinkedHashSet.empty());
	private final LinkedHashSet<NeqConstraint> constraints;

	public static NeqConstraints get(Package p) {
		return p.getStore(NeqConstraints.class);
	}

	public static List<NeqConstraint> getConstraints(Package p) {
		// newest-first, the iteration order reify has always rendered
		return get(p).getConstraints().toList().reverse();
	}

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	@Override
	protected LinkedHashSet<NeqConstraint> records() {
		return constraints;
	}

	@Override
	protected NeqConstraints make(LinkedHashSet<NeqConstraint> records) {
		return NeqConstraints.of(records);
	}

	@Override
	protected Class<NeqConstraint> recordClass() {
		return NeqConstraint.class;
	}

	@Override
	protected Option<Option<NeqConstraint>> verify(
			NeqConstraint record, Package state) {
		return Disequality.verifyAndSimplify(List.of(record), state.substitution())
				.map(List::headOption);
	}

	/**
	 * Lossless factoring: a record goes to the covered half iff every var it
	 * touches (LHS names and RHS term vars alike) is supplied.
	 */
	@Override
	protected boolean fits(NeqConstraint record, Set<Term<?>> covered) {
		for (Tuple2<Term<?>, Term<?>> pair : record.getSeparate()) {
			if (!covered.contains(pair._1) || escapes(pair._2, covered)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Records with their names translated through the renaming — LHS names
	 * map like any other (live var ↔ canonical hole), RHS terms map deeply.
	 */
	@Override
	protected Fiber<NeqConstraint> renamed(NeqConstraint record, Renaming renaming) {
		return record.getSeparate().foldLeft(
						Fiber.<HashMap<Term<?>, Term<?>>> done(HashMap.empty()),
						(acc, pair) -> acc.flatMap(renamedPairs -> renaming.apply(pair._1)
								.flatMap(lhs -> renaming.apply(pair._2)
										.map(rhs -> renamedPairs.put(lhs, rhs)))))
				.map(NeqConstraint::of);
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
