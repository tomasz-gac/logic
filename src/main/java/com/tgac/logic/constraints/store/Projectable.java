package com.tgac.logic.constraints.store;

// ABOUTME: A store whose knowledge can change variable namespaces: a semilattice
// ABOUTME: with rename and split — keys, seeding and answer replay are compositions.

import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The boundary capability, single-sorted: a store IS a residue over its own
 * names — live {@link LVar}s or canonical {@link com.tgac.logic.unification.Hole}s
 * alike — and every boundary operation is a composition of three primitives
 * over the store's own {@link Semilattice}:
 *
 * <pre>
 * key projection   = split(vars)._1.rename(of(varsToHoles))          — {@link #project}
 * master seeding   = absorb(key.rename(restating(holesToVars)))
 * answer capture   = rename(resolution) then rename(of(varsToHoles)) — Residues.normalize
 * answer replay    = absorb(rename(minting(holesToFresh)))           — ∃ by minting
 * </pre>
 *
 * Imposition is the DRIVER's: {@code Propagation.absorb(factor)} meets the
 * factor into its resident store and queues {@link Absorbable#normalize} —
 * the store owns what normal means, the driver owns statement. The ARRIVAL
 * half is {@link Absorbable}, a capability of its own (bulk-loadable does
 * not imply table-compatible); this interface adds the DEPARTURE half.
 *
 * Comparison (subsumption keys, entailment matching, dedup) is the lattice
 * order the store already has; a hole-named store compares structurally
 * across packages because holes are canonical names. There is no widening
 * parameter and no exactness refusal: keys widen by construction (the
 * caller keeps {@code split}'s covered half), and answers carry the whole
 * factor. Participation in tabling requires this capability — knowledge
 * that cannot cross namespaces cannot be keyed or cached, and unkeyed
 * knowledge means silently wrong reuse.
 *
 * <p>TERMINATION is a separate, undeclared concern: a store whose canonical
 * images over a fixed var list form a finite lattice bounds the tabling
 * ascent (FD does); one that does not (record sets over unbounded values)
 * can ascend forever on adversarial programs — the author's responsibility,
 * exactly like tabling an unbounded generator.
 */
public interface Projectable<S extends Projectable<S>> extends Crossing<S>, Absorbable<S> {

	/**
	 * This store's knowledge about the mapped vars in canonical names — each
	 * var to its slot hole, the correspondence reify built, carried as data.
	 * The comparable key citizen. Projecting an empty map of an empty store
	 * is the empty store: the triviality test is {@code isEmpty}.
	 */
	default Fiber<S> project(Map<LVar<?>, Hole<?>> slots) {
		return split(new ArrayList<>(slots.keySet()))._1.rename(Renaming.of(slots));
	}
}
