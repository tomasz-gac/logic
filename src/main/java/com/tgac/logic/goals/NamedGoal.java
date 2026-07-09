package com.tgac.logic.goals;

import com.tgac.logic.goals.optimizer.Optimizer;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.debug.DebugStore;
import com.tgac.logic.debug.Trace;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public
class NamedGoal implements Goal {
	Function<Package, String> label;
	Goal goal;

	@Override
	public Cont<Package, Nothing> apply(Package aPackage) {
		return DebugStore.from(aPackage)
				.map(store -> Trace.tracedCont(label, goal, store.getTracer(),
						aPackage.putStore(store.push(label.apply(aPackage))),
						answer -> answer.putStore(store)))
				.getOrElse(() -> goal.apply(aPackage));
	}

	@Override
	public Fiber<Goal> accept(Optimizer optimizer) {
		return optimizer.visit(this);
	}

	@Override
	public String toString() {
		return label.apply(Package.empty());
	}
}
