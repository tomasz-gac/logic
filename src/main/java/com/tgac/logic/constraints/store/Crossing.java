package com.tgac.logic.constraints.store;

// ABOUTME: The departure capability: knowledge that can leave its world —
// ABOUTME: split by vars, rename to other names. Arrival is Absorbable's, not ours.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.LVar;
import io.vavr.Tuple2;
import java.util.List;

/**
 * The DEPARTURE half of crossing worlds: what any factor of knowledge must
 * do to leave its package. Split factors knowledge losslessly; rename moves
 * it to another namespace. Deliberately free of {@link Absorbable} — how
 * knowledge ARRIVES is a fact about the factor kind (private factors ride
 * {@code Propagation.absorb}, the shared bindings factor arrives by
 * unification through the chokepoint), and a factor that can leave need not
 * be bulk-loadable.
 */
public interface Crossing<S extends Crossing<S>> {

	/**
	 * Lossless factoring: (the knowledge expressible over {@code vars}, the
	 * remainder) — {@code _1 ∧ _2 = this}. The factor decides what is
	 * separable (custody); the CALLER decides what to do with the halves.
	 */
	Tuple2<S, S> split(List<LVar<?>> vars);

	/**
	 * This knowledge under changed names. A {@link Fiber} because term
	 * rewriting rides the engine's traversals — callers compose, never
	 * {@code get}.
	 */
	Fiber<S> rename(Renaming renaming);
}
