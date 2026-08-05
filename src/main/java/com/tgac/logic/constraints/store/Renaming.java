package com.tgac.logic.constraints.store;

// ABOUTME: The name DICTIONARY knowledge needs to cross a boundary — one
// ABOUTME: implementation per crossing, constructed only through the factories.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The name DICTIONARY knowledge needs to cross a boundary. A name is a live
 * {@link LVar} or a canonical {@link Hole} — a store under holes IS its
 * canonical form, so live↔canonical conversion is just another renaming.
 * Each crossing is its own implementation with its own algorithm:
 * {@link #of} renames live vars ({@link VarRenaming} — fed the var↦hole
 * map reify built, it enters the slot namespace the comparability quotient
 * keys are made of); {@link #restating} leaves it onto given live targets
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
	static Renaming of(Map<? extends Term<?>, ? extends Term<?>> seed) {
		return new VarRenaming(seed);
	}

	/** Leaving with existential minting: {@code seed} maps names to targets; every miss mints a fresh var. */
	static Renaming minting(Map<? extends Term<?>, ? extends Term<?>> seed) {
		return new Minting(seed);
	}

	/** Leaving the canonical namespace: each hole onto its target — unlisted slots keep their names. */
	static Renaming restating(Map<? extends Hole<?>, ? extends Term<?>> slotTargets) {
		return new SlotRenaming(slotTargets);
	}

	/**
	 * Every NAME occurrence in the term — live vars and canonical holes —
	 * lazily streamed in traversal order: a short-circuiting consumer stops
	 * the scan early. Iterative, deep spines never recurse.
	 */
	static Stream<Term<?>> namesIn(Term<?> term) {
		Deque<Term<?>> work = new ArrayDeque<>();
		work.push(term);
		return StreamSupport.stream(new Spliterators.AbstractSpliterator<Term<?>>(
				Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
			@Override
			public boolean tryAdvance(Consumer<? super Term<?>> action) {
				while (!work.isEmpty()) {
					Term<?> current = work.pop();
					if (current.asVar().isDefined() || current.asReified().isDefined()) {
						action.accept(current);
						return true;
					}
					MiniKanren.members(current).forEach(members -> members.forEach(work::push));
				}
				return false;
			}
		}, false);
	}
}
