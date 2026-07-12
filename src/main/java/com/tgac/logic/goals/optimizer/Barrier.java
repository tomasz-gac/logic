package com.tgac.logic.goals.optimizer;

// ABOUTME: The one explicit boundary: optimize outside and inside, never across.
// ABOUTME: A leaf to every pass; interior structure still optimizes as it unfolds.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * The explicit form of the contract implicit barriers (impure goals, tabled
 * calls, opaque lambdas) already have: a partition point — it holds its
 * position and nothing reorders across it. The rewriter never enters, so a
 * hand-ordered conjunction inside is never re-sorted; interior defer
 * forcings still consult the ambient {@link OptimizerStore}, so structure
 * that UNFOLDS inside is still optimized. Protect what was written,
 * optimize what unfolds (docs/design/ambient-optimizer.md §5). Execution
 * delegates unchanged.
 */
@Value
@RequiredArgsConstructor(staticName = "of")
public class Barrier implements Goal {
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
		return "barrier(" + goal + ")";
	}
}
