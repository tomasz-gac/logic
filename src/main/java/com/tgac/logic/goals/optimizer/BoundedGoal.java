package com.tgac.logic.goals.optimizer;

// ABOUTME: A goal with a declared constant order — execution delegates unchanged,
// ABOUTME: the ordering pass reads the price.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class BoundedGoal implements Goal, Bounded {
	long order;
	Goal goal;

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return goal.apply(s);
	}

	@Override
	public long answers(Substitutions s) {
		return order;
	}

	@Override
	public String toString() {
		return goal.toString();
	}
}
