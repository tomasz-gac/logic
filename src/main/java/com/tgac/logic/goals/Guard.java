package com.tgac.logic.goals;

// ABOUTME: Marks a goal as deliberately ordered: optimizers treat it as a leaf
// ABOUTME: and never enter or reorder what it wraps.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * The explicit opt-out from optimization. {@link NamedGoal} is transparent to
 * optimizers (tracing must not disable them), so wrapping alone protects
 * nothing — a Guard is how a caller says "this order is deliberate".
 * Execution delegates unchanged.
 */
@Value
@RequiredArgsConstructor(staticName = "of")
public class Guard implements Goal {
	Goal goal;

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return goal.apply(s);
	}

	@Override
	public Fiber<Goal> accept(Optimizer optimizer) {
		return optimizer.visit(this);
	}

	@Override
	public String toString() {
		return "guard(" + goal + ")";
	}
}
