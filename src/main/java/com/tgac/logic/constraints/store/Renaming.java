package com.tgac.logic.constraints.store;

// ABOUTME: A name mapping applied to constraint knowledge — live vars and
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
 * The one operation knowledge needs to cross a boundary: change names. A
 * name is a live {@link LVar} or a canonical {@link Hole} — a store under
 * holes IS its canonical form, so live↔canonical conversion is just another
 * renaming. {@link #walking} normalizes (resolve through the home
 * substitutions, free names keep themselves; spent entries fall to values
 * and drop store-side). {@link #canonical} renames live vars to their slot
 * holes — the comparability quotient keys are made of. {@link #ofSlots}
 * renames slot holes onto live targets — master seeding. {@link #into}
 * retargets live vars — answer replay — and an UNSEEDED var mints a fresh
 * one, recorded, so later lookups (this store or another) agree: one
 * Renaming shared across a delivery keeps a local shared between stores one
 * variable, and the mint is the existential.
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

	/** Normalization: walk through {@code home}, free names keep themselves. */
	public static Renaming walking(Substitutions home) {
		return new Renaming(home, new java.util.HashMap<>(), false);
	}

	/** Retargeting: {@code seed} maps vars to targets; misses mint fresh vars. */
	public static Renaming into(Map<LVar<?>, Term<?>> seed) {
		return new Renaming(null, new java.util.HashMap<>(seed), true);
	}

	/** Into the canonical namespace: {@code vars.get(i)} ↦ {@code _.i}. */
	public static Renaming canonical(List<LVar<?>> vars) {
		Map<Term<?>, Term<?>> seed = new java.util.HashMap<>();
		for (int i = 0; i < vars.size(); i++) {
			seed.put(vars.get(i), Hole.of(i));
		}
		return new Renaming(null, seed, false);
	}

	/** Out of the canonical namespace: {@code _.i} ↦ {@code targets.get(i)}. */
	public static Renaming ofSlots(List<? extends Term<?>> slotTargets) {
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
				varSubstitution = varSubstitution.put((LVar<?>) name.asVar().get(), target(name));
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
