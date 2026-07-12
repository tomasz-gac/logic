package com.tgac.logic.goals.optimizer;

// ABOUTME: The ambient optimizer riding the Package (DebugStore pattern): state
// ABOUTME: flows through defer walls, so the pass is waiting when bodies unfold.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Substitutions;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Carries the optimizer pipeline with the solver state
 * (docs/design/ambient-optimizer.md). Seeded by {@link Goal#solve(com.tgac.logic.unification.Unifiable, Optimizer)};
 * consulted at exactly one hook — {@link Goal#defer} forcing — so freshly
 * materialized recursion layers are rewritten against live bindings.
 */
@Value
@RequiredArgsConstructor(staticName = "of")
public class OptimizerStore implements Store {
	Optimizer pipeline;

	public static Option<OptimizerStore> from(Package pkg) {
		return pkg.getStores().get(OptimizerStore.class).map(OptimizerStore.class::cast);
	}

	public Fiber<Goal> rewrite(Goal body, Substitutions s) {
		return body.accept(pipeline.with(s));
	}

	@Override
	public Store remove(Stored c) {
		return this;
	}

	@Override
	public Store prepend(Stored c) {
		return this;
	}

	@Override
	public boolean contains(Stored c) {
		return false;
	}
}
