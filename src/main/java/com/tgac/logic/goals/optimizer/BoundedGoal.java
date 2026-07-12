package com.tgac.logic.goals.optimizer;

// ABOUTME: A goal with a declared constant order — execution delegates unchanged,
// ABOUTME: the ordering pass reads the price.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import io.vavr.collection.LinkedHashMap;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class BoundedGoal implements Goal, Bounded {
	ToLongFunction<Package> order;
	Goal goal;

	@Override
	public Cont<Package, Nothing> apply(Package s) {
		return goal.apply(s);
	}

	@Override
	public long answers(Substitutions s) {
		// the blind view is a store-less package
		return order.applyAsLong(Package.of(s, LinkedHashMap.empty()));
	}

	@Override
	public long answers(Package p) {
		return order.applyAsLong(p);
	}

	@Override
	public String toString() {
		return goal.toString();
	}
}
