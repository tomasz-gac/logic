package com.tgac.logic.goals.optimizer;

// ABOUTME: The one explicit boundary: optimize outside and inside, never across.
// ABOUTME: A leaf to every pass; interior structure still optimizes as it unfolds.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import io.vavr.collection.LinkedHashMap;
import java.util.function.ToLongFunction;
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
public class Barrier implements Goal, Bounded {
	Goal goal;
	ToLongFunction<Package> order;

	private Barrier(Goal goal, ToLongFunction<Package> order) {
		this.goal = goal;
		this.order = order;
	}

	public static Barrier of(Goal goal) {
		return new Barrier(goal, p -> Long.MAX_VALUE);
	}

	/**
	 * A barrier that can price itself against the live state — a tabled call
	 * pricing its completed entry. MAX (the incomplete case) holds position
	 * exactly as an unpriced barrier does; a finite price is the immovability
	 * transition (docs/design/optimizer.md).
	 */
	public static Barrier priced(ToLongFunction<Package> order, Goal goal) {
		return new Barrier(goal, order);
	}

	@Override
	public long answers(Substitutions s) {
		return order.applyAsLong(Package.of(s, LinkedHashMap.empty()));
	}

	@Override
	public long answers(Package p) {
		return order.applyAsLong(p);
	}

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
