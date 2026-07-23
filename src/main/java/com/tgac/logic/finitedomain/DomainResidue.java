package com.tgac.logic.finitedomain;

// ABOUTME: FD's projected knowledge about a var list: slot i holds vars[i]'s
// ABOUTME: domain, absent slot = ⊤. A meet-valued map that is a meet-semilattice.

import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.logic.constraints.store.Residue;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import java.util.List;
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
 */
@Value(staticConstructor = "of")
public class DomainResidue implements MeetSemilattice<DomainResidue>, Residue<DomainResidue> {

	HashMap<Integer, Domain<?>> slots;

	/** Couplings expressible over the slots — replayed by {@link #restate}. */
	HashSet<CarriedConstraint> carried;

	public static DomainResidue of(HashMap<Integer, Domain<?>> slots) {
		return DomainResidue.of(slots, HashSet.empty());
	}

	@Override
	@SuppressWarnings("unchecked")
	public DomainResidue meet(DomainResidue other) {
		return DomainResidue.of(slots.merge(other.slots,
						(a, b) -> ((Domain<Object>) a).intersect((Domain<Object>) b)),
				carried.union(other.carried));
	}

	/**
	 * Entailment checked slotwise, without materializing the meet: every slot
	 * {@code other} constrains must be at-least-as-narrow here (an absent slot
	 * is ⊤, so it can entail nothing), and every coupling {@code other}
	 * carries must ride here too. The same order the meet derives — the laws
	 * test sweeps both against each other — at an early-exit, allocation-free
	 * cost, which matters because tabling folds this over every cached answer.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean leq(DomainResidue other) {
		return other.slots.forAll(slot -> slots.get(slot._1)
				.exists(mine -> ((Domain<Object>) mine).leq((Domain<Object>) slot._2)))
				&& carried.containsAll(other.carried);
	}

	/** One {@code dom} post per slot, then each carried coupling through its
	 * recipe — all public entries, so replay propagates like fresh knowledge
	 * (first examination, watchers, cascade). */
	@Override
	@SuppressWarnings("unchecked")
	public Goal restate(List<Unifiable<?>> vars) {
		Goal domains = slots.foldLeft(Goal.success(), (goal, slot) ->
				Conjunction.of(goal, FiniteDomain.dom(
						(Unifiable<Object>) vars.get(slot._1),
						(Domain<Object>) slot._2)));
		return carried.foldLeft(domains, (goal, coupling) ->
				Conjunction.of(goal, coupling.restate(vars)));
	}

	/** Any slot narrowed to nothing — the residue denotes an unsatisfiable region. */
	public boolean isEmpty() {
		return slots.values().exists(Domain::isEmpty);
	}
}
