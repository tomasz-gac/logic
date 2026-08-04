package com.tgac.logic.constraints.store;

// ABOUTME: The name DICTIONARY knowledge needs to cross a boundary — one
// ABOUTME: implementation per crossing, constructed only through the factories.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import java.util.List;
import java.util.Map;

/**
 * The name DICTIONARY knowledge needs to cross a boundary. A name is a live
 * {@link LVar} or a canonical {@link Hole} — a store under holes IS its
 * canonical form, so live↔canonical conversion is just another renaming.
 * Each crossing is its own implementation with its own algorithm:
 * {@link #of}/{@link #canonical} rename live vars ({@link VarRenaming} —
 * canonical enters the slot namespace, the comparability quotient keys are
 * made of); {@link #restating} leaves it onto given live targets
 * ({@link SlotRenaming}) — master seeding; {@link #minting} speaks both
 * namespaces and mints a fresh var for every unlisted name
 * ({@link Minting}) — answer replay, where the mint is the existential:
 * one Renaming shared across a delivery keeps a local shared between
 * stores one variable. Everywhere else, unlisted names keep themselves.
 *
 * <p>RESOLUTION is not a renaming of its own: these are dumb maps.
 * Rewriting terms to their current meanings under substitutions is the
 * answer side's own step — Residues builds the walked seed and feeds it
 * here like any other seed.
 */
public interface Renaming {

	/** The term under this renaming — deep: every name mapped. */
	Fiber<Term<?>> apply(Term<?> term);

	/** A live-var renaming from a seed map: unlisted names keep themselves. */
	static Renaming of(Map<? extends Term<?>, Term<?>> seed) {
		return new VarRenaming(seed);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	static Renaming minting(Map<? extends Term<?>, Term<?>> seed) {
		return new Minting(seed);
	}

	/** Entering the canonical namespace: {@code vars.get(i)} ↦ {@code _.i}. */
	static Renaming canonical(List<LVar<?>> vars) {
		Map<Term<?>, Term<?>> seed = new java.util.HashMap<>();
		for (int i = 0; i < vars.size(); i++) {
			seed.put(vars.get(i), Hole.of(i));
		}
		return of(seed);
	}

	/** Leaving the canonical namespace onto given targets: {@code _.i} ↦ {@code targets.get(i)}. */
	static Renaming restating(List<? extends Term<?>> slotTargets) {
		return new SlotRenaming(slotTargets);
	}
}
