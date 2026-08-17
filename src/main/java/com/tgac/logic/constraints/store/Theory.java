package com.tgac.logic.constraints.store;

// ABOUTME: The plan-space value: a family-homogeneous set of atoms with the
// ABOUTME: covering order — syntax of knowledge, no context, laws once.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * A factor's knowledge as SYNTAX: the set of atoms that state it, held as a
 * value. No context — bindings were baked into the atoms by whatever walk
 * produced them; applying new context is the execution plane's business
 * (absorb: fold {@code meet(Atom)} into a factor, then normalize). The
 * algebra is generic and lawful once: {@code meet} is union with
 * ENTAILMENT dedup — an atom strictly dominated by another drops
 * (subsumption deletion: stating ¬(A ∧ B) alongside ¬A adds nothing) —
 * and {@code leq} is the COVERING order — every atom of the wider theory
 * entailed by some atom of this one — grade two of the leq tower
 * (structural ⊂ covering ⊂ the factor's semantic leq): sound always,
 * sharp exactly as far as the atom classes' own {@code leq} overrides
 * reach, blind to conjunctive entailment by construction. The dedup is
 * what makes the two agree: on plain sets the covering order neither
 * reverses accumulation nor stays antisymmetric ({¬A} and {¬A, ¬(A∧B)}
 * cover each other); on subsumption-free sets both laws hold, as far as
 * the atom leq is itself antisymmetric mod equals — the sharp overrides'
 * obligation.
 *
 * <p>Admission is family-guarded: a theory only holds atoms whose
 * {@code getFactorClass} is compatible with its family token — the
 * homogeneity the type parameter promises, enforced at the door.
 */
public final class Theory<F extends Factor<F>> implements Semilattice<Theory<F>>, PartialOrder<Theory<F>> {

	private final Class<? extends Factor<?>> family;
	private final LinkedHashSet<Atom<F>> atoms;

	private Theory(Class<? extends Factor<?>> family, LinkedHashSet<Atom<F>> atoms) {
		this.family = family;
		this.atoms = atoms;
	}

	public static <F extends Factor<F>> Theory<F> empty(Class<F> family) {
		return new Theory<>(family, LinkedHashSet.empty());
	}

	public static <F extends Factor<F>> Theory<F> of(Class<? extends F> family, Iterable<? extends Atom<F>> atoms) {
		LinkedHashSet<Atom<F>> admitted = LinkedHashSet.empty();
		for (Atom<F> atom : atoms) {
			admitted = admitted.add(admit(family, atom));
		}
		return new Theory<>(family, minimal(admitted));
	}

	/**
	 * Subsumption deletion: drops every atom STRICTLY dominated by another
	 * (mutually-entailing distinct atoms both stay — dropping either would
	 * lose knowledge the other's structure does not carry).
	 */
	private static <F extends Factor<F>> LinkedHashSet<Atom<F>> minimal(LinkedHashSet<Atom<F>> atoms) {
		return atoms.filter(a -> !atoms.exists(b ->
				!b.equals(a) && b.leq(a) && !a.leq(b)));
	}

	private static <F extends Factor<F>> Atom<F> admit(Class<? extends Factor<?>> family, Atom<F> atom) {
		if (!family.isAssignableFrom(atom.getFactorClass())
				&& !atom.getFactorClass().isAssignableFrom(family)) {
			throw new IllegalArgumentException("a theory of " + family.getSimpleName()
					+ " refuses a " + atom.getFactorClass().getSimpleName() + " atom: " + atom);
		}
		return atom;
	}

	public Class<? extends Factor<?>> family() {
		return family;
	}

	public LinkedHashSet<Atom<F>> atoms() {
		return atoms;
	}

	public boolean isEmpty() {
		return atoms.isEmpty();
	}

	/** ⊗: union with entailment dedup — stating twice, or weaker, is stating once. */
	public Theory<F> meet(Theory<F> other) {
		if (!family.equals(other.family)) {
			throw new IllegalArgumentException("theories of different families do not meet: "
					+ family.getSimpleName() + " vs " + other.family.getSimpleName());
		}
		return new Theory<>(family, minimal(atoms.addAll(other.atoms)));
	}

	@Override
	public Theory<F> combine(Theory<F> other) {
		return meet(other);
	}

	/**
	 * The covering order: this ⊑ other iff every atom of {@code other} is
	 * entailed by SOME atom of this — delegates to {@link Atom#leq}, so its
	 * sharpness is exactly what the atom classes invest.
	 */
	@Override
	public boolean leq(Theory<F> other) {
		return other.atoms.forAll(b -> atoms.exists(a -> a.leq(b)));
	}

	/**
	 * The name cut: an atom is covered iff every name its watched surface
	 * touches is supplied (grounds are always covered); coupled atoms go to
	 * the remainder. {@code _1 ⊗ _2 = this}.
	 */
	public Tuple2<Theory<F>, Theory<F>> split(List<LVar<?>> vars) {
		LinkedHashSet<Atom<F>> covered = LinkedHashSet.empty();
		LinkedHashSet<Atom<F>> remainder = LinkedHashSet.empty();
		for (Atom<F> atom : atoms) {
			boolean in = atom.watched()
					.flatMap(MiniKanren::namesIn)
					.allMatch(vars::contains);
			if (in) {
				covered = covered.add(atom);
			} else {
				remainder = remainder.add(atom);
			}
		}
		return Tuple.of(new Theory<>(family, covered), new Theory<>(family, remainder));
	}

	/**
	 * The crossing, atom by atom; refuses loudly on an untranscribable atom.
	 * Re-minimalizes: a renaming that merges names can create dominations
	 * the source theory did not have.
	 */
	@SuppressWarnings("unchecked")
	public Fiber<Theory<F>> rename(Renaming renaming) {
		Fiber<LinkedHashSet<Atom<F>>> renamed = Fiber.done(LinkedHashSet.empty());
		for (Atom<F> atom : atoms) {
			if (!(atom instanceof Transcribable)) {
				throw new IllegalStateException("a theory cannot cross with an untranscribable atom: " + atom);
			}
			renamed = renamed.flatMap(acc ->
					((Transcribable<? extends Atom<F>>) atom).rename(renaming).map(acc::add));
		}
		return renamed.map(as -> new Theory<>(family, minimal(as)));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Theory)) {
			return false;
		}
		Theory<?> other = (Theory<?>) o;
		return family.equals(other.family) && atoms.equals(other.atoms);
	}

	@Override
	public int hashCode() {
		return Objects.hash(family, atoms);
	}

	@Override
	public String toString() {
		return family.getSimpleName() + atoms.mkString("{", " ⊗ ", "}");
	}
}
