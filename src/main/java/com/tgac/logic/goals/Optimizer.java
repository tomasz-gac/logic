package com.tgac.logic.goals;

// ABOUTME: A visitor over the goal combinators — the seam for goal-tree rewriting.
// ABOUTME: The generic visit(Goal) overload is the extension hook for foreign goal types.

import com.tgac.functional.fibers.Fiber;

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

	Fiber<Goal> visit(Guard guard);
}
