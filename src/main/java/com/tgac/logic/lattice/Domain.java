package com.tgac.logic.lattice;

// ABOUTME: The component lattice a LatticeStore attaches to a name: a meet-semilattice
// ABOUTME: value carrying its own capability record — membership, collapse, stabilization.

import com.tgac.functional.algebra.Bottomed;
import com.tgac.functional.algebra.MeetSemilattice;
import io.vavr.control.Option;

/**
 * What a {@link LatticeStore} requires of its per-name values — the capability
 * record of docs/design/lattice-store.md §2, carried by the value itself. The
 * record is the ADMISSION TEST: it sorts every candidate domain in one glance
 * (finite sets: everything; reals: no exact stabilization; labels: pure meet)
 * and it is where an instance's hazards are declared rather than discovered.
 */
public interface Domain<L extends Domain<L>> extends MeetSemilattice<L>, Bottomed {

	/** Does a ground value lie in this domain? Revise's verification. */
	boolean admits(Object ground);

	/**
	 * The single value this domain has collapsed to, if any — the store turns
	 * it into an inferred binding.
	 */
	Option<Object> asPoint();

	/**
	 * The termination guard of wake-on-narrowing: is this domain (the met
	 * result) no new knowledge over {@code previous}? Re-examination is
	 * licensed only by strict narrowing, so do not weaken it. The default —
	 * exact equality — is the finite-descent policy; a lattice with infinite
	 * descending chains (reals) MUST override with its ε/widening policy, a
	 * declared precision rather than cleverness.
	 */
	default boolean stabilized(L previous) {
		return equals(previous);
	}
}
