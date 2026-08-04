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
import java.util.function.Function;

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
 * <p>RESOLUTION is not a mode of this class: rewriting terms to their
 * current meanings under substitutions is its caller's own step, built on
 * {@link #of(Function)} — this class never sees a Substitutions.
 */
public final class Renaming {

	private final Function<Term<?>, Term<?>> lookup;
	private final Map<Term<?>, Term<?>> minted;
	private final boolean mintOnMiss;

	private Renaming(Function<Term<?>, Term<?>> lookup, Map<Term<?>, Term<?>> minted, boolean mintOnMiss) {
		this.lookup = lookup;
		this.minted = minted;
		this.mintOnMiss = mintOnMiss;
	}

	/** A renaming from a name lookup: {@code null} keeps the name. */
	public static Renaming of(Function<Term<?>, Term<?>> lookup) {
		return new Renaming(lookup, new java.util.HashMap<>(), false);
	}

	/** A renaming from a seed map: unlisted names keep themselves. */
	public static Renaming of(Map<? extends Term<?>, Term<?>> seed) {
		Map<Term<?>, Term<?>> copy = new java.util.HashMap<>(seed);
		return new Renaming(copy::get, copy, false);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	public static Renaming minting(Map<? extends Term<?>, Term<?>> seed) {
		Map<Term<?>, Term<?>> copy = new java.util.HashMap<>(seed);
		return new Renaming(copy::get, copy, true);
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
	public Term<?> apply(Term<?> term) {
		Set<Term<?>> names = namesOf(term);
		if (names.isEmpty()) {
			return term;
		}
		if (names.size() == 1 && isName(term)) {
			return target(term);
		}
		Term<?> walked = term;
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
		Term<?> known = minted.get(name);
		if (known == null) {
			known = lookup.apply(name);
		}
		if (known != null) {
			return known;
		}
		if (!mintOnMiss) {
			return name;
		}
		Term<?> fresh = LVar.lvar();
		minted.put(name, fresh);
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
