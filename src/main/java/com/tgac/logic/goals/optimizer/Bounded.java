package com.tgac.logic.goals.optimizer;

// ABOUTME: A goal that can bound how many answers it may emit from a given state —
// ABOUTME: the order function driving the ordering optimizer's ascending sort.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Substitutions;

/**
 * The capability interface of the narrowing/widening taxonomy: leaves declare
 * their own order — the maximum number of answers they may emit under the
 * given bindings (docs/design/optimizer.md §3-4). Combinators derive theirs
 * (product over conjunction, sum over disjunction); {@code Long.MAX_VALUE}
 * means no bound is estimable.
 */
public interface Bounded {
	long answers(Substitutions s);

	/** Wrap a goal with a declared constant order — the retrofit for opaque factories. */
	static Goal of(long order, Goal goal) {
		return BoundedGoal.of(order, goal);
	}
}
