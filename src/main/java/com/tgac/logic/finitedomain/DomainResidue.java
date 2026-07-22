package com.tgac.logic.finitedomain;

// ABOUTME: FD's projected knowledge about a var list: slot i holds vars[i]'s
// ABOUTME: domain, absent slot = ⊤. A meet-valued map that is a meet-semilattice.

import com.tgac.functional.algebra.MeetSemilattice;
import io.vavr.collection.HashMap;
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
 */
@Value(staticConstructor = "of")
public class DomainResidue implements MeetSemilattice<DomainResidue> {

	HashMap<Integer, Domain<?>> slots;

	@Override
	@SuppressWarnings("unchecked")
	public DomainResidue meet(DomainResidue other) {
		return DomainResidue.of(slots.merge(other.slots,
				(a, b) -> ((Domain<Object>) a).intersect((Domain<Object>) b)));
	}

	/** Any slot narrowed to nothing — the residue denotes an unsatisfiable region. */
	public boolean isEmpty() {
		return slots.values().exists(Domain::isEmpty);
	}
}
