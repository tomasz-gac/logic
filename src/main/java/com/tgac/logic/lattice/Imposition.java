package com.tgac.logic.lattice;

// ABOUTME: A domain value keyed to its target, as a Stored item — the statement
// ABOUTME: unit of a lattice store. Consumed by stated, never resident.

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
public class Imposition<L extends Domain<L>> implements Stored {
	Class<? extends Store> storeClass;
	Term<?> target;
	L value;

	@Override
	public Stream<Term<?>> terms() {
		return Stream.of(target);
	}

	@Override
	public String toString() {
		return target + " ⊂ " + value;
	}
}
