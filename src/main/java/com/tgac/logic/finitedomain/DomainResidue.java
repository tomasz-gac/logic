package com.tgac.logic.finitedomain;

// ABOUTME: FD's projected knowledge about a var list: slot i holds vars[i]'s
// ABOUTME: domain, absent slot = ⊤. A meet-valued map that is a meet-semilattice.

import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.logic.constraints.store.Residue;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The finite-domain store's residue over a positional var list — what
 * {@code project} returns and {@code restate} re-imposes. Slots are keyed by
 * position in the caller's list; an ABSENT slot is ⊤ (no knowledge), which
 * also spares FD a ⊤ domain object ("all values" is not a finite domain).
 *
 * <p>Pointwise meet with absent-as-⊤ makes the residue itself a
 * {@link MeetSemilattice}: entailment ({@code leq}) is derived, so residues
 * compare with one call — the driver-side fold TCLP needs. Termination note:
 * residues over a FIXED var list form a finite lattice (subsets of finite
 * domains), the sufficient condition for a bounded tabling ascent.
 *
 * <p>{@code widened} is ADVISORY ({@link Residue#isWidened}) and excluded
 * from equality: it says the store's live knowledge exceeded this vocabulary
 * (a propagator watching a supplied var), not what region this residue
 * states. Meet propagates it disjunctively — combined knowledge understates
 * if either side did.
 */
@Value(staticConstructor = "of")
public class DomainResidue implements MeetSemilattice<DomainResidue>, Residue<DomainResidue> {

	HashMap<Integer, Domain<?>> slots;

	@EqualsAndHashCode.Exclude
	boolean widened;

	public static DomainResidue of(HashMap<Integer, Domain<?>> slots) {
		return DomainResidue.of(slots, false);
	}

	@Override
	@SuppressWarnings("unchecked")
	public DomainResidue meet(DomainResidue other) {
		return DomainResidue.of(slots.merge(other.slots,
						(a, b) -> ((Domain<Object>) a).intersect((Domain<Object>) b)),
				widened || other.widened);
	}

	/** One {@code dom} post per slot — the public entry, so replay propagates
	 * like fresh knowledge (first examination, watchers, cascade). */
	@Override
	@SuppressWarnings("unchecked")
	public Goal restate(List<Unifiable<?>> vars) {
		return slots.foldLeft(Goal.success(), (goal, slot) ->
				Conjunction.of(goal, FiniteDomain.dom(
						(Unifiable<Object>) vars.get(slot._1),
						(Domain<Object>) slot._2)));
	}

	/** Any slot narrowed to nothing — the residue denotes an unsatisfiable region. */
	public boolean isEmpty() {
		return slots.values().exists(Domain::isEmpty);
	}
}
