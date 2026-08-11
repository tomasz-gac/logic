package com.tgac.logic.lattice;

// ABOUTME: A domain value keyed to its target, as a Stored item — the statement
// ABOUTME: unit of a lattice store. Consumed by stated, never resident.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Transcribable;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import java.util.stream.Stream;
import lombok.Value;

/**
 * "{@code target ⊂ value}" as an item: stated through the chokepoint, the
 * owning {@link LatticeStore} consumes it in its {@code stated} trigger —
 * update's verification/collapse/narrowing routing, inside the store's
 * method. The item itself never persists: the values map is the knowledge,
 * so {@code prepend} deliberately ignores it.
 */
@Value
public class Imposition<L extends Domain<L>> implements Stored, Transcribable {
	Class<? extends Store> storeClass;
	Term<?> target;
	L value;

	@Override
	public Stream<Term<?>> terms() {
		return Stream.of(target);
	}

	/** The value is ground data and rides; only the target re-keys. */
	@Override
	public Fiber<Stored> rename(Renaming renaming) {
		return renaming.apply(target)
				.map(renamed -> new Imposition<>(storeClass, renamed, value));
	}

	@Override
	public String toString() {
		return target + " ⊂ " + value;
	}
}
