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
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * A factor's knowledge as SYNTAX: the set of atoms that state it, held as a
 * value. No context — bindings were baked into the atoms by whatever walk
 * produced them; applying new context is the execution plane's business
 * (absorb: fold {@code meet(Atom)} into a factor, then normalize). The
 * algebra is generic and lawful once: {@code meet} is insert/filter —
 * union, then slot-mates that declare the {@link Semilattice} capability
 * combine (same name, same watched surface: what an atom kind knows about
 * its own digestion — {@code x⊂{1,2} ⊗ x⊂{2,3} = x⊂{2}}), then ENTAILMENT
 * dedup: an atom strictly dominated by another drops (subsumption
 * deletion: stating ¬(A ∧ B) alongside ¬A adds nothing). {@code leq} is
 * the COVERING order — every atom of the wider theory
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
 * <p>Family homogeneity is the type parameter's promise alone — atoms are
 * typed over their factor, so a theory of {@code F} can only be handed
 * {@code F}'s atoms; there is no runtime door.
 */

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Theory<F extends Factor<F>> implements Semilattice<Theory<F>>, PartialOrder<Theory<F>> {

	private final LinkedHashSet<Atom<F>> atoms;

	/**
	 * The slot index, derived from {@code atoms} (excluded from equality):
	 * collision key → the atoms on that surface. A capability kind's bucket
	 * is always the singleton fused representative; undeclared kinds share
	 * the bucket. Meet merges indexes — each atom's surface is collected
	 * once, at the door it entered through, never per comparison.
	 */
	private final LinkedHashMap<Slot, LinkedHashSet<Atom<F>>> slots;

	/** The collision key: same name, same watched surface = same slot. */
	@Value
	private static class Slot {
		String name;
		HashSet<Term<?>> surface;
	}

	private static Slot slotOf(Atom<?> atom) {
		return new Slot(atom.name(),
				HashSet.ofAll(atom.watched().collect(Collectors.toList())));
	}

	public static <F extends Factor<F>> Theory<F> empty() {
		return new Theory<>(LinkedHashSet.empty(), LinkedHashMap.empty());
	}

	public static <F extends Factor<F>> Theory<F> of(Iterable<? extends Atom<F>> atoms) {
		return digested(atoms);
	}

	/** Normal form: fuse slot-mates, then delete dominated — every door digests. */
	private static <F extends Factor<F>> Theory<F> digested(Iterable<? extends Atom<F>> in) {
		LinkedHashMap<Slot, LinkedHashSet<Atom<F>>> slots = LinkedHashMap.empty();
		for (Atom<F> atom : in) {
			slots = inserted(slots, slotOf(atom), atom);
		}
		return pruned(slots);
	}

	/**
	 * The capability meet: an atom kind that declares {@link Semilattice}
	 * knows how to digest its own slot-mates — combining is family
	 * knowledge, read here as a capability, never assumed. Undeclared kinds
	 * union into the bucket.
	 */
	private static <F extends Factor<F>> LinkedHashMap<Slot, LinkedHashSet<Atom<F>>> inserted(
			LinkedHashMap<Slot, LinkedHashSet<Atom<F>>> slots, Slot slot, Atom<F> atom) {
		LinkedHashSet<Atom<F>> bucket = slots.get(slot).getOrElse(LinkedHashSet.empty());
		if (atom instanceof Semilattice && bucket.nonEmpty()) {
			return slots.put(slot, LinkedHashSet.of(fuse(bucket.head(), atom)));
		}
		return slots.put(slot, bucket.add(atom));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <F extends Factor<F>> Atom<F> fuse(Atom<F> a, Atom<F> b) {
		return (Atom<F>) ((Semilattice) a).combine((Semilattice) b);
	}

	/** The domination filter over the fused buckets; rebuilt only on a kill. */
	private static <F extends Factor<F>> Theory<F> pruned(LinkedHashMap<Slot, LinkedHashSet<Atom<F>>> slots) {
		LinkedHashSet<Atom<F>> flat = slots.values()
				.foldLeft(LinkedHashSet.empty(), LinkedHashSet::addAll);
		LinkedHashSet<Atom<F>> kept = minimal(flat);
		if (kept.size() == flat.size()) {
			return new Theory<>(flat, slots);
		}
		return new Theory<>(kept, slots
				.mapValues(bucket -> bucket.filter(kept::contains))
				.filterValues(LinkedHashSet::nonEmpty));
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

	public LinkedHashSet<Atom<F>> atoms() {
		return atoms;
	}

	public boolean isEmpty() {
		return atoms.isEmpty();
	}

	/**
	 * ⊗: insert/filter — union, fuse slot-mates, delete dominated. An index
	 * merge: both sides' surfaces are already collected, so the fusion phase
	 * is linear in the right-hand theory.
	 */
	public Theory<F> meet(Theory<F> other) {
		LinkedHashMap<Slot, LinkedHashSet<Atom<F>>> merged = slots;
		for (Tuple2<Slot, LinkedHashSet<Atom<F>>> entry : other.slots) {
			for (Atom<F> atom : entry._2) {
				merged = inserted(merged, entry._1, atom);
			}
		}
		return pruned(merged);
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
		return Tuple.of(digested(covered), digested(remainder));
	}

	/**
	 * The crossing, atom by atom. Re-digests: a renaming that merges names
	 * can create slot collisions and dominations the source theory did not
	 * have.
	 */
	public Fiber<Theory<F>> rename(Renaming renaming) {
		Fiber<LinkedHashSet<Atom<F>>> renamed = Fiber.done(LinkedHashSet.empty());
		for (Atom<F> atom : atoms) {
			renamed = renamed.flatMap(acc ->
					atom.rename(renaming).map(acc::add));
		}
		return renamed.map(Theory::digested);
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
		return atoms.equals(other.atoms);
	}

	@Override
	public int hashCode() {
		return Objects.hash(atoms);
	}

	@Override
	public String toString() {
		return atoms.mkString("{", " ⊗ ", "}");
	}
}
