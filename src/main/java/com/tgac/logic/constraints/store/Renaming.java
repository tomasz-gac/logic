package com.tgac.logic.constraints.store;

// ABOUTME: Name dictionaries applied to constraint knowledge — one class per
// ABOUTME: crossing: var renaming, slot restating, and minting replay (∃).

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The name DICTIONARY knowledge needs to cross a boundary. A name is a live
 * {@link LVar} or a canonical {@link Hole} — a store under holes IS its
 * canonical form, so live↔canonical conversion is just another renaming.
 * Each crossing is its own class with its own algorithm:
 * {@link #of}/{@link #canonical} rename live vars ({@code walkAll} under a
 * fixed substitution — canonical enters the slot namespace, the
 * comparability quotient keys are made of); {@link #restating} leaves it
 * onto given live targets by positional {@code instantiate} — master
 * seeding; {@link #minting} speaks both namespaces and mints a fresh var
 * for every unlisted name — answer replay, where the mint is the
 * existential: one Renaming shared across a delivery keeps a local shared
 * between stores one variable. Everywhere else, unlisted names keep
 * themselves.
 *
 * <p>RESOLUTION is not a mode of this class: this is a dumb map. Rewriting
 * terms to their current meanings under substitutions is the answer side's
 * own step — Residues builds the walked seed and feeds it here like any
 * other seed.
 */
public abstract class Renaming {

	Renaming() {
	}

	/** The term under this renaming — deep: every name mapped. */
	public abstract Fiber<Term<?>> apply(Term<?> term);

	/** A live-var renaming from a seed map: unlisted names keep themselves. */
	public static Renaming of(Map<? extends Term<?>, Term<?>> seed) {
		return new VarRenaming(seed);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	public static Renaming minting(Map<? extends Term<?>, Term<?>> seed) {
		return new Minting(seed);
	}

	/** Entering the canonical namespace: {@code vars.get(i)} ↦ {@code _.i}. */
	public static Renaming canonical(List<LVar<?>> vars) {
		Map<Term<?>, Term<?>> seed = new java.util.HashMap<>();
		for (int i = 0; i < vars.size(); i++) {
			seed.put(vars.get(i), Hole.of(i));
		}
		return of(seed);
	}

	/** Leaving the canonical namespace onto given targets: {@code _.i} ↦ {@code targets.get(i)}. */
	public static Renaming restating(List<? extends Term<?>> slotTargets) {
		return new SlotRenaming(slotTargets);
	}

	private static boolean isName(Term<?> t) {
		return t.asVar().isDefined() || t.asReified().isDefined();
	}

	/** Live vars to their targets: {@code walkAll} under a fixed substitution. */
	private static final class VarRenaming extends Renaming {
		private final Substitutions substitutions;

		VarRenaming(Map<? extends Term<?>, Term<?>> seed) {
			this.substitutions = Substitutions.of(seed.entrySet().stream()
					// an identity entry means "keep" — walk's chain-follower
					// must never see a self-binding
					.filter(entry -> !entry.getKey().equals(entry.getValue()))
					.collect(HashMap.collector(entry -> varName(entry.getKey()), Map.Entry::getValue)));
		}

		private static LVar<?> varName(Term<?> name) {
			return name.asVar().<LVar<?>> map(var -> var)
					.getOrElseThrow(() -> new IllegalArgumentException(
							"a var renaming takes live var names — slot names cross by "
									+ "restating or minting: " + name));
		}

		@Override
		public Fiber<Term<?>> apply(Term<?> term) {
			return renamesAnyIn(term)
					? MiniKanren.walkAll(substitutions, term).map(t -> t)
					: Fiber.done(term);
		}

		/** walkAll rebuilds structure wholesale — an untouched term must pass by identity. */
		private boolean renamesAnyIn(Term<?> term) {
			Deque<Term<?>> work = new ArrayDeque<>();
			work.push(term);
			while (!work.isEmpty()) {
				Term<?> current = work.pop();
				if (current.asVar().isDefined()) {
					if (substitutions.binding(current.asVar().get()) != null) {
						return true;
					}
				} else {
					MiniKanren.members(current).forEach(members -> members.forEach(work::push));
				}
			}
			return false;
		}
	}

	/** Slot holes to their positional targets: {@code instantiate} over the slot list. */
	private static final class SlotRenaming extends Renaming {
		private final List<? extends Term<?>> slotTargets;

		SlotRenaming(List<? extends Term<?>> slotTargets) {
			this.slotTargets = slotTargets;
		}

		@Override
		public Fiber<Term<?>> apply(Term<?> term) {
			int maxSlot = maxSlotIn(term);
			if (maxSlot < 0) {
				return Fiber.done(term);
			}
			// instantiate MINTS holes it has no entry for; an unlisted slot
			// must keep its name instead
			List<Term<?>> bySlot = IntStream.rangeClosed(0, maxSlot)
					.<Term<?>> mapToObj(i -> i < slotTargets.size() ? slotTargets.get(i) : Hole.of(i))
					.collect(Collectors.toList());
			return MiniKanren.instantiate(term, bySlot).map(t -> t);
		}

		/** Deepest slot number occurring in the term, -1 when hole-free — iterative, deep spines must not recurse. */
		private static int maxSlotIn(Term<?> term) {
			int maxSlot = -1;
			Deque<Term<?>> work = new ArrayDeque<>();
			work.push(term);
			while (!work.isEmpty()) {
				Term<?> current = work.pop();
				if (current.asReified().isDefined()) {
					maxSlot = Math.max(maxSlot, ((Hole<?>) current).getNumber());
				} else {
					MiniKanren.members(current).forEach(members -> members.forEach(work::push));
				}
			}
			return maxSlot;
		}
	}

	/**
	 * Both namespaces at once, minting a fresh var for every unlisted name —
	 * the existential: mints are RECORDED, so occurrences across one
	 * delivery (and across stores sharing this instance) become one
	 * variable.
	 */
	private static final class Minting extends Renaming {
		private final Map<Term<?>, Term<?>> targets;

		Minting(Map<? extends Term<?>, Term<?>> seed) {
			this.targets = new java.util.HashMap<>(seed);
		}

		@Override
		public Fiber<Term<?>> apply(Term<?> term) {
			Set<Term<?>> names = namesIn(term);
			if (names.isEmpty()) {
				return Fiber.done(term);
			}
			names.forEach(name -> targets.computeIfAbsent(name, miss -> LVar.lvar()));
			HashMap<LVar<?>, Term<?>> varTargets = names.stream()
					.flatMap(name -> name.asVar().toJavaStream())
					// an identity entry means "keep" — walk's chain-follower
					// must never see a self-binding
					.filter(name -> !name.equals(targets.get(name)))
					.collect(HashMap.collector(name -> name, targets::get));
			int maxSlot = names.stream()
					.filter(name -> !name.asVar().isDefined())
					.map(Hole.class::cast)
					.mapToInt(Hole::getNumber)
					.max().orElse(-1);
			Fiber<Term<?>> renamedVars = varTargets.isEmpty()
					? Fiber.done(term)
					: MiniKanren.walkAll(Substitutions.of(varTargets), term).map(t -> t);
			if (maxSlot < 0) {
				return renamedVars;
			}
			List<Term<?>> bySlot = IntStream.rangeClosed(0, maxSlot)
					.<Term<?>> mapToObj(i -> targets.getOrDefault(Hole.of(i), Hole.of(i)))
					.collect(Collectors.toList());
			return renamedVars.flatMap(r -> MiniKanren.instantiate(r, bySlot).map(t -> t));
		}

		/** Iterative structural scan — deep spines must not recurse. */
		private static Set<Term<?>> namesIn(Term<?> t) {
			Set<Term<?>> names = new LinkedHashSet<>();
			Deque<Term<?>> work = new ArrayDeque<>();
			work.push(t);
			while (!work.isEmpty()) {
				Term<?> current = work.pop();
				if (isName(current)) {
					names.add(current);
				} else {
					MiniKanren.members(current).forEach(members -> members.forEach(work::push));
				}
			}
			return names;
		}
	}
}
