package com.tgac.logic.lattice;

// ABOUTME: A domain value keyed to its target, as a Atom item — the statement
// ABOUTME: unit of a lattice store. Consumed by stated, never resident.

import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashSet;
import io.vavr.collection.Traversable;
import lombok.Getter;
import lombok.Value;

/**
 * "{@code target ⊂ value}" as an item: stated through the chokepoint, the
 * owning {@link LatticeFactor} consumes it in its {@code stated} trigger —
 * update's verification/collapse/narrowing routing, inside the store's
 * method. The item itself never persists: the values map is the knowledge,
 * so {@code prepend} deliberately ignores it.
 *
 * <p>The declared {@link Semilattice} is the capability the theory meet
 * reads: same-target impositions combine to their domain meet — the value
 * plane digesting without context. A ⊥ result ({@code target ⊂ ∅}) is a
 * legal plan value; only execution reads it as failure.
 */
@Value
public class Imposition<L extends Domain<L>, F extends Factor<F>> implements Atom<F>, Semilattice<Imposition<L, F>> {
	Class<? extends F> storeClass;
	Term<?> target;
	@Getter
	L value;

	/** Same family, same target only — the slot condition, guarded loudly. */
	@Override
	public Imposition<L, F> combine(Imposition<L, F> other) {
		if (!storeClass.equals(other.storeClass) || !target.equals(other.target)) {
			throw new IllegalArgumentException(
					"impositions combine only on their own slot: " + this + " vs " + other);
		}
		return new Imposition<>(storeClass, target, value.meet(other.value));
	}

	/** Sharp over same-slot domains (same family, same target); structural otherwise. */
	@SuppressWarnings("unchecked")
	@Override
	public boolean leq(Atom<F> other) {
		if (other instanceof Imposition
				&& storeClass.equals(((Imposition<?, ?>) other).storeClass)
				&& target.equals(((Imposition<?, ?>) other).target)) {
			return value.leq((L) ((Imposition<?, ?>) other).value);
		}
		return equals(other);
	}

	@Override
	public Class<? extends F> getFactorClass() {
		return storeClass;
	}

	@Override
	public String name() {
		return "imposition";
	}

	@Override
	public Traversable<Term<?>> watched() {
		return HashSet.of(target);
	}

	/** The value is ground data and rides; only the target re-keys. */
	@Override
	public Fiber<Atom<F>> rename(Renaming renaming) {
		return renaming.apply(target)
				.map(renamed -> new Imposition<>(storeClass, renamed, value));
	}

	@Override
	public String toString() {
		return target + " ⊂ " + value;
	}
}
