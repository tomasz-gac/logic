package com.tgac.logic.constraints.store;

// ABOUTME: The plan-space value: a family-homogeneous set of atoms with the
// ABOUTME: covering order — syntax of knowledge, no context, laws once.

import com.tgac.functional.algebra.Absorbing;
import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Name;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.Traversable;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
public final class Theory<F extends Factor<F>> implements Semilattice<Theory<F>>, PartialOrder<Theory<F>>, Absorbing {

	private final LinkedHashSet<Atom<F>> atoms;


	/**
	 * The slot index, derived from {@code atoms} (excluded from equality):
	 * collision key → THE atom on that slot, one hop. Single occupancy is
	 * the atom kinds' obligation — a kind whose knowledge accumulates on one
	 * surface holds it as a collection and declares {@link Semilattice}.
	 * The key is the collection the atom already holds, so its equality is
	 * the kind's identity granularity and keying costs nothing.
	 */
	private final LinkedHashMap<Slot, Atom<F>> slots;

	/**
	 * The by-kind index, derived like {@code slots} (excluded from equality):
	 * concrete atom class → its atoms, so a factor's kind-specific reads
	 * (impositions, propagators, residents) iterate exactly their kind
	 * instead of filtering the whole bag.
	 */
	private final LinkedHashMap<Class<?>, LinkedHashSet<Atom<F>>> kinds;

	/**
	 * The watchers index, derived like {@code slots} (excluded from equality):
	 * NAME — a live LVar or a canonical Any — → the atoms whose watched
	 * surface holds it. What {@link #renamedReporting} selects work by, so a
	 * crossing prices at the touched atoms, not the theory.
	 */
	private final LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> watchers;

	/**
	 * The ⊥ flag, computed at digestion (excluded from equality — equality
	 * is the atoms): an atom declaring the {@link Absorbing} capability and
	 * answering true makes the whole theory refutational. A legal plan
	 * value; only execution reads it as failure.
	 */
	private final boolean absorbing;

	/**
	 * The collision key: same family, same name, same held watched
	 * collection = same slot. The family rides in the key because atom
	 * names are unique only WITHIN a family (every lattice family names
	 * its value atoms "imposition").
	 */
	@Value
	private static class Slot {
		Class<?> family;
		String name;
		Traversable<Term<?>> surface;
	}

	private static Slot slotOf(Atom<?> atom) {
		return new Slot(atom.getFactorClass(), atom.name(), atom.watched());
	}

	public static <F extends Factor<F>> Theory<F> empty() {
		return new Theory<>(LinkedHashSet.empty(), LinkedHashMap.empty(), LinkedHashMap.empty(),
				LinkedHashMap.empty(), false);
	}

	@Override
	public boolean isAbsorbing() {
		return absorbing;
	}

	private static boolean absorbs(Atom<?> atom) {
		return atom instanceof Absorbing && ((Absorbing) atom).isAbsorbing();
	}

	public static <F extends Factor<F>> Theory<F> of(Iterable<? extends Atom<F>> atoms) {
		return digested(atoms);
	}

	/** Normal form: fuse slot-mates, then delete dominated — every door digests. */
	private static <F extends Factor<F>> Theory<F> digested(Iterable<? extends Atom<F>> in) {
		LinkedHashMap<Slot, Atom<F>> slots = LinkedHashMap.empty();
		for (Atom<F> atom : in) {
			slots = inserted(slots, slotOf(atom), atom);
		}
		return pruned(slots);
	}

	/**
	 * The capability meet: an atom kind that declares {@link Semilattice}
	 * knows how to digest its own slot-mates — combining is family
	 * knowledge, read here as a capability, never assumed. A kind that
	 * cannot combine yet collides has broken the single-occupancy
	 * obligation: hold the collection inside the atom.
	 */
	private static <F extends Factor<F>> LinkedHashMap<Slot, Atom<F>> inserted(
			LinkedHashMap<Slot, Atom<F>> slots, Slot slot, Atom<F> atom) {
		return slots.get(slot)
				.map(occupant -> {
					if (occupant.equals(atom)) {
						return slots;
					}
					if (occupant instanceof Semilattice && atom instanceof Semilattice) {
						return slots.put(slot, fuse(occupant, atom));
					}
					throw new IllegalStateException(
							"slot-mates that cannot combine — the kind must hold its collection: "
									+ occupant + " vs " + atom);
				})
				.getOrElse(() -> slots.put(slot, atom));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <F extends Factor<F>> Atom<F> fuse(Atom<F> a, Atom<F> b) {
		return (Atom<F>) ((Semilattice) a).combine((Semilattice) b);
	}

	/** The domination filter over the fused slots; rebuilt only on a kill. */
	private static <F extends Factor<F>> Theory<F> pruned(LinkedHashMap<Slot, Atom<F>> slots) {
		LinkedHashSet<Atom<F>> flat = LinkedHashSet.ofAll(slots.values());
		LinkedHashSet<Atom<F>> kept = minimal(flat);
		if (kept.size() == flat.size()) {
			return new Theory<>(flat, slots, kindsOf(flat), watchersOf(flat),
					flat.exists(Theory::absorbs));
		}
		return new Theory<>(kept, slots.filterValues(kept::contains), kindsOf(kept),
				watchersOf(kept), kept.exists(Theory::absorbs));
	}

	private static <F extends Factor<F>> LinkedHashMap<Class<?>, LinkedHashSet<Atom<F>>> kindsOf(
			LinkedHashSet<Atom<F>> atoms) {
		return atoms.foldLeft(LinkedHashMap.empty(), (kinds, atom) ->
				kinds.put(atom.getClass(), kinds.get(atom.getClass())
						.getOrElse(LinkedHashSet.empty())
						.add(atom)));
	}

	private static <F extends Factor<F>> LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> watchersOf(
			LinkedHashSet<Atom<F>> atoms) {
		LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> index = LinkedHashMap.empty();
		for (Atom<F> atom : atoms) {
			index = watcherAdded(index, atom);
		}
		return index;
	}

	private static <F extends Factor<F>> LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> watcherAdded(
			LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> index, Atom<F> atom) {
		LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> result = index;
		for (Term<?> term : atom.watched()) {
			for (Iterator<Name<?>> names = MiniKanren.namesIn(term).iterator(); names.hasNext(); ) {
				Name<?> name = names.next();
				result = result.put(name, result.get(name)
						.getOrElse(LinkedHashSet.empty())
						.add(atom));
			}
		}
		return result;
	}

	private static <F extends Factor<F>> LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> watcherRemoved(
			LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> index, Atom<F> atom) {
		LinkedHashMap<Name<?>, LinkedHashSet<Atom<F>>> result = index;
		for (Term<?> term : atom.watched()) {
			for (Iterator<Name<?>> names = MiniKanren.namesIn(term).iterator(); names.hasNext(); ) {
				Name<?> name = names.next();
				LinkedHashSet<Atom<F>> bucket = result.get(name)
						.getOrElse(LinkedHashSet.empty())
						.remove(atom);
				result = bucket.isEmpty() ? result.remove(name) : result.put(name, bucket);
			}
		}
		return result;
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

	/** The slot occupant, one hop — the factor's by-surface read. */
	public Option<Atom<F>> atom(Class<?> family, String name, Traversable<Term<?>> surface) {
		return slots.get(new Slot(family, name, surface));
	}

	/**
	 * One kind's atoms, streamed — the factor's kind-specific iteration.
	 * Buckets are keyed by CONCRETE class and matched by assignability, so
	 * an abstract kind (Propagator's schema subclasses) streams all its
	 * implementors; the scan is over the handful of distinct classes, not
	 * the atoms.
	 */
	public <K> java.util.stream.Stream<K> kind(Class<K> kind) {
		return kinds.toJavaStream()
				.filter(entry -> kind.isAssignableFrom(entry._1))
				.flatMap(entry -> entry._2.toJavaStream())
				.map(kind::cast);
	}

	/**
	 * The incremental door: insert one atom, fusing at its slot — NO
	 * domination sweep. Agrees with {@link #meet} exactly when the kinds'
	 * leq is slot-local (impositions, propagators — the lattice family);
	 * a family with cross-slot entailment (nogood subsumption) must meet.
	 */
	public Theory<F> with(Atom<F> atom) {
		Slot slot = slotOf(atom);
		Option<Atom<F>> occupant = slots.get(slot);
		if (!occupant.isDefined()) {
			return new Theory<>(atoms.add(atom), slots.put(slot, atom),
					kindAdded(kinds, atom), watcherAdded(watchers, atom),
					absorbing || absorbs(atom));
		}
		Atom<F> prior = occupant.get();
		if (prior.equals(atom)) {
			return this;
		}
		if (prior instanceof Semilattice && atom instanceof Semilattice) {
			Atom<F> fused = fuse(prior, atom);
			return new Theory<>(atoms.remove(prior).add(fused), slots.put(slot, fused),
					kindAdded(kindRemoved(kinds, prior), fused),
					watcherAdded(watcherRemoved(watchers, prior), fused),
					absorbing || absorbs(fused));
		}
		throw new IllegalStateException(
				"slot-mates that cannot combine — the kind must hold its collection: "
						+ prior + " vs " + atom);
	}

	private static <F extends Factor<F>> LinkedHashMap<Class<?>, LinkedHashSet<Atom<F>>> kindAdded(
			LinkedHashMap<Class<?>, LinkedHashSet<Atom<F>>> kinds, Atom<F> atom) {
		return kinds.put(atom.getClass(), kinds.get(atom.getClass())
				.getOrElse(LinkedHashSet.empty())
				.add(atom));
	}

	private static <F extends Factor<F>> LinkedHashMap<Class<?>, LinkedHashSet<Atom<F>>> kindRemoved(
			LinkedHashMap<Class<?>, LinkedHashSet<Atom<F>>> kinds, Atom<F> atom) {
		LinkedHashSet<Atom<F>> bucket = kinds.get(atom.getClass())
				.getOrElse(LinkedHashSet.empty())
				.remove(atom);
		return bucket.isEmpty() ?
				kinds.remove(atom.getClass()) :
				kinds.put(atom.getClass(), bucket);
	}

	/**
	 * Occupant removal — NOT an algebra operation (knowledge only grows
	 * under ⊗): the execution plane's own-factor surgery, for a store
	 * discharging spent entries and subsumed propagators. A non-occupant
	 * leaves the theory untouched.
	 */
	public Theory<F> without(Atom<F> atom) {
		Slot slot = slotOf(atom);
		return slots.get(slot)
				.filter(atom::equals)
				.map(occupant -> {
					LinkedHashSet<Atom<F>> left = atoms.remove(occupant);
					return new Theory<F>(left, slots.remove(slot),
							kindRemoved(kinds, occupant),
							watcherRemoved(watchers, occupant),
							absorbing && left.exists(Theory::absorbs));
				})
				.getOrElse(this);
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
		LinkedHashMap<Slot, Atom<F>> merged = slots;
		for (Tuple2<Slot, Atom<F>> entry : other.slots) {
			merged = inserted(merged, entry._1, entry._2);
		}
		return pruned(merged);
	}

	@Override
	public Theory<F> combine(Theory<F> other) {
		return meet(other);
	}

	/**
	 * {@link #meet}, reporting: the met theory plus exactly the atoms that
	 * CHANGED — inserted, or fused to a new occupant. Covered and duplicate
	 * atoms are absent, and so is an incoming atom the domination filter
	 * killed (covered knowledge wakes no one). An empty report means the
	 * meet moved nothing and the receiver rides through by identity — what
	 * the doors read to skip.
	 */
	public Revised<F> metReporting(Theory<F> other) {
		LinkedHashMap<Slot, Atom<F>> merged = slots;
		List<Atom<F>> candidates = new ArrayList<>();
		for (Tuple2<Slot, Atom<F>> entry : other.slots) {
			Option<Atom<F>> occupant = merged.get(entry._1);
			if (!occupant.isDefined()) {
				merged = merged.put(entry._1, entry._2);
				candidates.add(entry._2);
				continue;
			}
			Atom<F> prior = occupant.get();
			if (prior.equals(entry._2)) {
				continue;
			}
			if (prior instanceof Semilattice && entry._2 instanceof Semilattice) {
				Atom<F> fused = fuse(prior, entry._2);
				if (!fused.equals(prior)) {
					merged = merged.put(entry._1, fused);
					candidates.add(fused);
				}
				continue;
			}
			throw new IllegalStateException(
					"slot-mates that cannot combine — the kind must hold its collection: "
							+ prior + " vs " + entry._2);
		}
		if (candidates.isEmpty()) {
			return new Revised<>(this, LinkedHashSet.empty());
		}
		Theory<F> met = pruned(merged);
		// a candidate the domination filter killed was covered knowledge; on a
		// subsumption-free receiver its death implies the meet moved nothing else
		LinkedHashSet<Atom<F>> changed = LinkedHashSet.ofAll(candidates)
				.filter(met.atoms::contains);
		return changed.isEmpty() ? new Revised<>(this, changed) : new Revised<>(met, changed);
	}

	/**
	 * {@link #rename}, reporting and indexed: only atoms whose watched
	 * surface holds a name in the renaming's domain are renamed; the rest
	 * ride by identity. Changed is read AFTER re-digestion — a renamed atom
	 * that collided reports its fusion, one that deduplicated against an
	 * untouched resident reports nothing. Fixed-seed renamings only; a
	 * minting renaming's domain grows as it mints, so replay keeps the
	 * plain {@link #rename}.
	 */
	public Fiber<Revised<F>> renamedReporting(Renaming renaming) {
		LinkedHashSet<Atom<F>> touched = LinkedHashSet.empty();
		for (Name<?> name : renaming.domain()) {
			touched = touched.addAll(watchers.get(name).getOrElse(LinkedHashSet.empty()));
		}
		if (touched.isEmpty()) {
			return Fiber.done(new Revised<>(this, LinkedHashSet.empty()));
		}
		LinkedHashSet<Atom<F>> untouched = atoms.removeAll(touched);
		Fiber<LinkedHashSet<Atom<F>>> renamed = Fiber.done(untouched);
		for (Atom<F> atom : touched) {
			renamed = renamed.flatMap(acc -> atom.rename(renaming).map(acc::add));
		}
		return renamed.map(all -> {
			Theory<F> result = digested(all);
			LinkedHashSet<Atom<F>> changed = result.atoms.filter(a -> !untouched.contains(a));
			return new Revised<>(result, changed);
		});
	}

	/** The atoms whose watched surface holds {@code name} — the index read. */
	public LinkedHashSet<Atom<F>> watchers(Name<?> name) {
		return watchers.get(name).getOrElse(LinkedHashSet.empty());
	}

	/**
	 * ⊥ absorbs: accumulation against a refuted branch contributes nothing —
	 * the guard the factor order carried before residence moved. The live
	 * half IS the covering order read backwards: meeting me into {@code
	 * other} adds nothing iff other already entails every atom of mine —
	 * allocation-free, early-exit, never a scratch meet.
	 */
	@Override
	public boolean absorbedBy(Theory<F> other) {
		return other.absorbing || other.leq(this);
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
			boolean in = atom.watched().forAll(term ->
					MiniKanren.namesIn(term).allMatch(vars::contains));
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
