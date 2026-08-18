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
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Package;
import io.vavr.collection.HashSet;
import io.vavr.collection.Traversable;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode(of = {"storeClass", "target", "value"})
public class Imposition<L extends Domain<L>, F extends Factor<F>> implements Atom<F>, Semilattice<Imposition<L, F>> {
	Class<? extends F> storeClass;
	Term<?> target;
	@Getter
	L value;

	/** The family's empty — the registration seed; statement context, outside identity. */
	F empty;

	/**
	 * The statement: registration seeds the family when absent; doomed under
	 * partial knowledge exactly when the value cannot stand against the live
	 * state — a ground target the value refuses, or a live entry it meets to
	 * bottom.
	 */
	@Override
	public Posting posting() {
		return Propagation.activate(this,
				p -> p.getStores().containsKey(storeClass) ? p : p.withStore(empty),
				this::doomed);
	}

	@SuppressWarnings("unchecked")
	private boolean doomed(Package p) {
		Term<?> walked = p.substitution().walk(target);
		if (walked.asVal().isDefined()) {
			return !value.admits(walked.get());
		}
		return p.getStores().get(storeClass)
				.flatMap(store -> ((LatticeFactor<L, ?>) store).getValue(walked))
				.exists(existing -> existing.meet(value).isAbsorbing());
	}

	/** Same family, same target only — the slot condition, guarded loudly. */
	@Override
	public Imposition<L, F> combine(Imposition<L, F> other) {
		if (!storeClass.equals(other.storeClass) || !target.equals(other.target)) {
			throw new IllegalArgumentException(
					"impositions combine only on their own slot: " + this + " vs " + other);
		}
		return new Imposition<>(storeClass, target, value.meet(other.value), empty);
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
				.map(renamed -> new Imposition<>(storeClass, renamed, value, empty));
	}

	@Override
	public String toString() {
		return target + " ⊂ " + value;
	}
}
