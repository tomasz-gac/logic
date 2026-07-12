package com.tgac.logic.goals.optimizer;

// ABOUTME: A goal that can bound how many answers it may emit from a given state —
// ABOUTME: the order function driving the ordering optimizer's ascending sort.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import java.util.function.ToLongFunction;

/**
 * The capability interface of the narrowing/widening taxonomy: leaves declare
 * their own order — the maximum number of answers they may emit under the
 * given bindings (docs/design/optimizer.md §3-4). Combinators derive theirs
 * (product over conjunction, sum over disjunction); {@code Long.MAX_VALUE}
 * means no bound is estimable.
 */
public interface Bounded {
	long answers(Substitutions s);

	/**
	 * Package-sighted order: store knowledge (live domains, completed table
	 * counts) is congealed speculation — exactly the estimator's diet
	 * (docs/design/lattice.md §5a). Store-aware leaves override; the default
	 * delegates to the substitution-blind estimate.
	 */
	default long answers(Package p) {
		return answers(p.substitution());
	}

	/** Wrap a goal with a declared constant order — the retrofit for opaque factories. */
	static Goal of(long order, Goal goal) {
		return BoundedGoal.of(s -> order, goal);
	}

	/**
	 * Wrap a goal with a SELF-PRICING order: the goal's own substitution-level
	 * decision fragment, run at pricing time (failure found there is failure
	 * forever — monotone). Must stay O(walk)-class; store-level trials belong
	 * to the probe pass, not pricing.
	 */
	static Goal of(ToLongFunction<Substitutions> order, Goal goal) {
		return BoundedGoal.of(order, goal);
	}
}
