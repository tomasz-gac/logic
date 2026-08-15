package com.tgac.logic.goals;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.debug.DebugStore;
import com.tgac.logic.debug.Trace;
import com.tgac.logic.goals.optimizer.Optimizer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public
class NamedGoal implements Goal {
	Function<Package, Fiber<String>> label;
	Goal goal;

	@Override
	public Cont<Package, Nothing> apply(Package aPackage) {
		return DebugStore.from(aPackage)
				.map(store -> Cont.<Package, Nothing> defer(() ->
						label.apply(aPackage).map(rendered ->
								Trace.tracedCont(label, goal, store.getTracer(),
										aPackage.putStore(store.push(rendered)),
										answer -> answer.putStore(store)))))
				.getOrElse(() -> goal.apply(aPackage));
	}

	@Override
	public Fiber<Goal> accept(Optimizer optimizer) {
		return optimizer.visit(this);
	}

	@Override
	public String toString() {
		Fiber<String> rendered = label.apply(Package.empty());
		// a state-rendered label may need the engine; display degrades to the
		// body rather than ground a fiber here
		return rendered.isDone() ? rendered.getDone("NamedGoal.toString") : goal.toString();
	}
}
