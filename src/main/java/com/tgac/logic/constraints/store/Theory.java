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
import io.vavr.control.Option;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

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

@RequiredArgsConstructor
public final class Theory<F extends Factor<F>> implements Semilattice<Theory<F>>, PartialOrder<Theory<F>> {
	private final LinkedHashSet<Atom<F>> atoms;

	public static <F extends Factor<F>> Theory<F> empty() {
		return new Theory<>(LinkedHashSet.empty());
	}

	public static <F extends Factor<F>> Theory<F> of(Iterable<? extends Atom<F>> atoms) {
		LinkedHashSet<Atom<F>> admitted = LinkedHashSet.empty();
		for (Atom<F> atom : atoms) {
			admitted = admitted.add(atom);
		}
		return new Theory<>(digested(admitted));
	}

	/** Normal form: fuse slot-mates, then delete dominated — every door digests. */
	private static <F extends Factor<F>> LinkedHashSet<Atom<F>> digested(LinkedHashSet<Atom<F>> atoms) {
		return minimal(fused(atoms));
	}

	/**
	 * The capability meet: an atom kind that declares {@link Semilattice}
	 * knows how to digest its own slot-mates (same name, same watched
	 * surface) — combining is family knowledge, read here as a capability,
	 * never assumed. Undeclared kinds union.
	 */
	private static <F extends Factor<F>> LinkedHashSet<Atom<F>> fused(LinkedHashSet<Atom<F>> atoms) {
		LinkedHashSet<Atom<F>> out = LinkedHashSet.empty();
		for (Atom<F> atom : atoms) {
			Option<Atom<F>> mate = out.find(prior -> fusable(prior, atom));
			if (mate.isDefined()) {
				out = out.remove(mate.get()).add(fuse(mate.get(), atom));
			} else {
				out = out.add(atom);
			}
		}
		return out;
	}

	private static boolean fusable(Atom<?> a, Atom<?> b) {
		return a instanceof Semilattice && b instanceof Semilattice
				&& a.name().equals(b.name())
				&& watchedSurface(a).equals(watchedSurface(b));
	}

	private static Set<Term<?>> watchedSurface(Atom<?> atom) {
		return atom.watched().collect(Collectors.toSet());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <F extends Factor<F>> Atom<F> fuse(Atom<F> a, Atom<F> b) {
		return (Atom<F>) ((Semilattice) a).combine((Semilattice) b);
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

	/** ⊗: insert/filter — union, fuse slot-mates, delete dominated. */
	public Theory<F> meet(Theory<F> other) {
		return new Theory<>(digested(atoms.addAll(other.atoms)));
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
		return Tuple.of(new Theory<>(covered), new Theory<>(remainder));
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
		return renamed.map(as -> new Theory<>(digested(as)));
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
