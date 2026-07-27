package com.tgac.logic.lattice;

// ABOUTME: The component lattice a LatticeStore attaches to a name: a meet-semilattice
// ABOUTME: value carrying its own capability record — membership, collapse, stabilization.

import com.tgac.functional.algebra.Absorbing;
import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.algebra.Semilattice;
import io.vavr.control.Option;

/**
 * What a {@link LatticeStore} requires of its per-name values — the capability
 * record of docs/design/lattice-store.md Â§2, carried by the value itself. The
 * record is the ADMISSION TEST: it sorts every candidate domain in one glance
 * (finite sets: everything; reals: no exact stabilization; labels: pure meet)
 * and it is where an instance's hazards are declared rather than discovered.
 */
public interface Domain<L extends Domain<L>> extends Semilattice<L>, PartialOrder<L>, Absorbing {

	/**
	 * Knowledge values with a meet: the accumulation order is the knowledge
	 * order REVERSED - accumulating constraints descends the extension. The
	 * direction matters to CONSUMERS: staleness and cache-fallback theorems
	 * are one-directional (stale bounds stay sound where data shrinks and lie
	 * where it grows), and entailment is free from the meet -
	 * {@code a ⊑ b ⟺ a ∧ b = a}.
	 */
	L meet(L other);

	@Override
	default L combine(L other) {
		return meet(other);
	}

	@Override
	default boolean leq(L other) {
		return meet(other).equals(this);
	}

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
	 * licensed only by strict narrowing, so do not weaken it. The default -
	 * exact equality - is the finite-descent policy; a lattice with infinite
	 * descending chains (reals) MUST override with its ε/widening policy, a
	 * declared precision rather than cleverness.
	 */
	default boolean stabilized(L previous) {
		return equals(previous);
	}
}
