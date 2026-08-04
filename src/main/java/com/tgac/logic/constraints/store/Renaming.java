package com.tgac.logic.constraints.store;

// ABOUTME: A name DICTIONARY applied to constraint knowledge — live vars and
// ABOUTME: canonical holes are both names; misses keep their name or mint fresh (∃).

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
 * <p>RESOLUTION is deliberately not a public mode: rewriting terms to their
 * current meanings under substitutions (spent entries fall to values and
 * drop store-side) is a different operation that merely shares this
 * class's traversal — callers use {@link Projectable#walked}, which
 * bridges to it internally, so "walk, then translate" reads as two steps.
 */
public final class Renaming {

	private final Substitutions home;
	private final Map<Term<?>, Term<?>> targets;
	private final boolean mintOnMiss;

	private Renaming(Substitutions home, Map<Term<?>, Term<?>> targets, boolean mintOnMiss) {
		this.home = home;
		this.targets = targets;
		this.mintOnMiss = mintOnMiss;
	}

	/** The resolution bridge for {@link Projectable#walked} — not a public mode. */
	static Renaming resolving(Substitutions home) {
		return new Renaming(home, new java.util.HashMap<>(), false);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	public static Renaming minting(Map<? extends Term<?>, Term<?>> seed) {
		return new Renaming(null, new java.util.HashMap<>(seed), true);
	}

	/** Entering the canonical namespace: {@code vars.get(i)} ↦ {@code _.i}. */
	public static Renaming canonical(List<LVar<?>> vars) {
		Map<Term<?>, Term<?>> seed = new java.util.HashMap<>();
		for (int i = 0; i < vars.size(); i++) {
			seed.put(vars.get(i), Hole.of(i));
		}
		return new Renaming(null, seed, false);
	}

	/** Leaving the canonical namespace onto given targets: {@code _.i} ↦ {@code targets.get(i)}. */
	public static Renaming restating(List<? extends Term<?>> slotTargets) {
		Map<Term<?>, Term<?>> seed = new java.util.HashMap<>();
		for (int i = 0; i < slotTargets.size(); i++) {
			seed.put(Hole.of(i), slotTargets.get(i));
		}
		return new Renaming(null, seed, false);
	}

	/** The term under this renaming — deep: walked, then every name mapped. */
	public Term<?> apply(Term<?> term) {
		Term<?> walked = home == null ? term : MiniKanren.walkAll(home, term).get();
		Set<Term<?>> names = namesOf(walked);
		if (names.isEmpty()) {
			return walked;
		}
		if (names.size() == 1 && isName(walked)) {
			return target(walked);
		}
		HashMap<LVar<?>, Term<?>> varSubstitution = HashMap.empty();
		int maxSlot = -1;
		for (Term<?> name : names) {
			if (name.asVar().isDefined()) {
				varSubstitution = varSubstitution.put(name.asVar().get(), target(name));
			} else {
				maxSlot = Math.max(maxSlot, ((Hole<?>) name).getNumber());
			}
		}
		Term<?> replaced = varSubstitution.isEmpty()
				? walked
				: MiniKanren.walkAll(Substitutions.of(varSubstitution), walked).get();
		if (maxSlot < 0) {
			return replaced;
		}
		List<Term<?>> bySlot = new ArrayList<>();
		for (int i = 0; i <= maxSlot; i++) {
			Hole<?> hole = Hole.of(i);
			bySlot.add(names.contains(hole) ? target(hole) : hole);
		}
		return MiniKanren.instantiate(replaced, bySlot).get();
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
