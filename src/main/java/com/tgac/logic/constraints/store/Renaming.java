package com.tgac.logic.constraints.store;

// ABOUTME: A name DICTIONARY applied to constraint knowledge — live vars and
// ABOUTME: canonical holes are both names; misses keep their name or mint fresh (∃).

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The name DICTIONARY knowledge needs to cross a boundary. A name is a live
 * {@link LVar} or a canonical {@link Hole} — a store under holes IS its
 * canonical form, so live↔canonical conversion is just another renaming.
 * Two public directions and one miss policy:
 * {@link #canonical} enters the slot namespace (live vars to their slot
 * holes — the comparability quotient keys are made of); {@link #restating}
 * leaves it onto given live targets — master seeding; {@link #minting}
 * leaves it with fresh names on every miss — answer replay, where the mint
 * is the existential: one Renaming shared across a delivery keeps a local
 * shared between stores one variable.
 *
 * <p>RESOLUTION is not a mode of this class: this is a dumb map. Rewriting
 * terms to their current meanings under substitutions is the answer side's
 * own step — Residues builds the walked seed and feeds it here like any
 * other seed.
 */
public final class Renaming {

	private final Map<Term<?>, Term<?>> targets;
	private final boolean mintOnMiss;

	private Renaming(Map<Term<?>, Term<?>> targets, boolean mintOnMiss) {
		this.targets = targets;
		this.mintOnMiss = mintOnMiss;
	}

	/** A renaming from a seed map: unlisted names keep themselves. */
	public static Renaming of(Map<? extends Term<?>, Term<?>> seed) {
		return new Renaming(new java.util.HashMap<>(seed), false);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	public static Renaming minting(Map<? extends Term<?>, Term<?>> seed) {
		return new Renaming(new java.util.HashMap<>(seed), true);
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
		Map<Term<?>, Term<?>> seed = new java.util.HashMap<>();
		for (int i = 0; i < slotTargets.size(); i++) {
			seed.put(Hole.of(i), slotTargets.get(i));
		}
		return of(seed);
	}

	/** The term under this renaming — deep: every name mapped. */
	public Fiber<Term<?>> apply(Term<?> term) {
		Set<Term<?>> names = namesOf(term);
		if (names.isEmpty()) {
			return Fiber.done(term);
		}
		if (names.size() == 1 && isName(term)) {
			return Fiber.done(target(term));
		}
		HashMap<LVar<?>, Term<?>> substitutedVars = HashMap.empty();
		int maxSlot = -1;
		for (Term<?> name : names) {
			if (name.asVar().isDefined()) {
				Term<?> mapped = target(name);
				// a kept name must stay OUT of the replacement map: a
				// self-binding sends walk's chain-follower into a loop
				if (mapped != name) {
					substitutedVars = substitutedVars.put(name.asVar().get(), mapped);
				}
			} else {
				maxSlot = Math.max(maxSlot, ((Hole<?>) name).getNumber());
			}
		}
		Fiber<Term<?>> replaced = substitutedVars.isEmpty()
				? Fiber.done(term)
				: MiniKanren.walkAll(Substitutions.of(substitutedVars), term).map(t -> t);
		if (maxSlot < 0) {
			return replaced;
		}
		List<Term<?>> bySlot = new ArrayList<>();
		for (int i = 0; i <= maxSlot; i++) {
			Hole<?> hole = Hole.of(i);
			bySlot.add(names.contains(hole) ? target(hole) : hole);
		}
		return replaced.flatMap(r -> MiniKanren.instantiate(r, bySlot).map(t -> t));
	}

	private Term<?> target(Term<?> name) {
		Term<?> known = targets.get(name);
		if (known != null) {
			return known;
		}
		if (!mintOnMiss) {
			return name;
		}
		Term<?> fresh = LVar.lvar();
		targets.put(name, fresh);
		return fresh;
	}

	private static boolean isName(Term<?> t) {
		return t.asVar().isDefined() || t.asReified().isDefined();
	}

	/** Iterative structural scan — deep spines must not recurse. */
	private static Set<Term<?>> namesOf(Term<?> t) {
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
