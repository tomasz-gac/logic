package com.tgac.logic.goals.optimizer;

// ABOUTME: A visitor over the goal combinators — the seam for goal-tree rewriting.
// ABOUTME: The generic visit(Goal) overload is the extension hook for foreign goal types.

import com.tgac.logic.goals.Conde;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.NamedGoal;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Substitutions;

/**
 * Rewrites goal trees before execution. Dispatch is double: goals implement
 * {@link Goal#accept}, combinators route to their own overload, and everything
 * else — opaque lambdas, committed choice, foreign goal types — lands in the
 * generic {@link #visit(Goal)}, which downstream optimizers override to
 * recognise their own goals (a planner's lookup goals, say).
 *
 * <p>Contract: a pass must preserve the binding environment at every goal it
 * does not itself own — reordering is legal only within runs of owned goals
 * between unrecognised barriers. This protects committed choice AND tabling
 * (table entries are keyed on call-argument boundness; moving a binder across
 * a tabled call multiplies its table keys per value).
 *
 * <p>Optimizers compose as an ordered pipeline of passes, never by merging
 * visitors. No pass needs fixpoint iteration: normalization is a single
 * bottom-up traversal.
 */
public interface Optimizer {

	/** The fallback and extension hook: anything unrecognised is a barrier. */
	Fiber<Goal> visit(Goal goal);

	Fiber<Goal> visit(Conjunction conjunction);

	Fiber<Goal> visit(Conde conde);

	Fiber<Goal> visit(NamedGoal named);

	Fiber<Goal> visit(Barrier barrier);

	/**
	 * Pass-state injection: a substitution-aware pass returns a copy carrying
	 * {@code s}; static passes ignore it. Called by {@link OptimizerStore} at
	 * the defer hook with the live bindings.
	 */
	default Optimizer with(Substitutions s) {
		return this;
	}

	/** Sequential composition — passes compose as a pipeline, never by merging. */
	static Optimizer pipeline(Optimizer first, Optimizer second) {
		return new Optimizer() {
			private Fiber<Goal> both(Goal g) {
				return g.accept(first).flatMap(r -> r.accept(second));
			}

			@Override
			public Fiber<Goal> visit(Goal goal) {
				return both(goal);
			}

			@Override
			public Fiber<Goal> visit(Conjunction conjunction) {
				return both(conjunction);
			}

			@Override
			public Fiber<Goal> visit(Conde conde) {
				return both(conde);
			}

			@Override
			public Fiber<Goal> visit(NamedGoal named) {
				return both(named);
			}

			@Override
			public Fiber<Goal> visit(Barrier barrier) {
				return both(barrier);
			}

			@Override
			public Optimizer with(Substitutions s) {
				return pipeline(first.with(s), second.with(s));
			}
		};
	}
}
