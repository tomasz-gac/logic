package com.tgac.logic.constraints.store;

// ABOUTME: The name DICTIONARY knowledge needs to cross a boundary — one map,
// ABOUTME: one engine (walkAll), one miss policy: keep yourself, or mint (∃).

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unknown;
import io.vavr.collection.HashMap;
import java.util.Map;

/**
 * The name DICTIONARY knowledge needs to cross a boundary. A name is a live
 * {@link LVar} or a canonical {@link Hole} — one {@link Unknown} type — so a
 * renaming is literally the map type the engine already walks: seeds go in,
 * {@code walkAll} carries them, and the crossings differ only in seed shape
 * and miss policy. {@link #of} keeps unlisted names ({@link #restating} is
 * its slot-keyed reading — master seeding); {@link #minting} mints a fresh
 * var for every unlisted name — answer replay, where the mint is the
 * existential: mints are RECORDED, so one Renaming shared across a delivery
 * keeps a local shared between stores one variable.
 *
 * <p>RESOLUTION is not a mode of this class: this is a dumb map. Rewriting
 * terms to their current meanings under substitutions is the answer side's
 * own step — Residues builds the walked seed and feeds it here like any
 * other seed.
 */
public final class Renaming {

	private final Map<Unknown<?>, Term<?>> targets;
	private final boolean mintOnMiss;

	private Renaming(Map<Unknown<?>, Term<?>> targets, boolean mintOnMiss) {
		this.targets = targets;
		this.mintOnMiss = mintOnMiss;
	}

	/** A renaming from a seed map: unlisted names keep themselves. */
	public static Renaming of(Map<? extends Unknown<?>, ? extends Term<?>> seed) {
		return new Renaming(named(seed), false);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	public static Renaming minting(Map<? extends Unknown<?>, ? extends Term<?>> seed) {
		return new Renaming(named(seed), true);
	}

	/** Leaving the canonical namespace: {@code _.i} ↦ its target — unlisted slots keep their names. */
	public static Renaming restating(Map<? extends Hole<?>, ? extends Term<?>> slotTargets) {
		return of(slotTargets);
	}

	/**
	 * An identity entry means "keep" — it stays out of the map, so walk's
	 * chain-follower never sees a self-binding. Keys are NAMES by type;
	 * raw-typed abuse dies on the erased cast at this boundary.
	 */
	private static Map<Unknown<?>, Term<?>> named(Map<? extends Unknown<?>, ? extends Term<?>> seed) {
		Map<Unknown<?>, Term<?>> targets = new java.util.LinkedHashMap<>();
		seed.forEach((name, target) -> {
			if (!name.equals(target)) {
				targets.put(name, target);
			}
		});
		return targets;
	}

	/** The term under this renaming — deep: every name mapped, one walk. */
	public Fiber<Term<?>> apply(Term<?> term) {
		if (mintOnMiss) {
			MiniKanren.namesIn(term).forEach(name -> targets.computeIfAbsent(name, miss -> LVar.lvar()));
		}
		// walkAll rebuilds structure wholesale — an untouched term must pass by identity
		return targets.isEmpty() || MiniKanren.namesIn(term).noneMatch(targets::containsKey)
				? Fiber.done(term)
				: MiniKanren.walkAll(Substitutions.of(seed()), term).map(t -> t);
	}

	private HashMap<Unknown<?>, Term<?>> seed() {
		return HashMap.ofAll(targets);
	}
}
