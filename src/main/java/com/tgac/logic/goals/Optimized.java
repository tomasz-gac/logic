package com.tgac.logic.goals;

// ABOUTME: A goal that rewrites itself through an Optimizer when applied — apply
// ABOUTME: time is the only time recursive unfoldings are visible to rewriting.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Runs the optimizer over the wrapped goal at apply time, then executes the
 * rewritten tree. Construction time sees a {@code defer} wall around
 * recursion; applying per-layer lets each unfolding be rewritten as it
 * materializes (combinators that own recursion — tabling — wrap their own
 * bodies, since the plan is then computed once per table variant).
 */
@Value
@RequiredArgsConstructor(staticName = "of")
public class Optimized implements Goal {
	Goal goal;
	Optimizer optimizer;

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return Cont.defer(() -> goal.accept(optimizer)
				.map(g -> g.apply(s)));
	}

	@Override
	public String toString() {
		return "optimized(" + goal + ")";
	}
}
