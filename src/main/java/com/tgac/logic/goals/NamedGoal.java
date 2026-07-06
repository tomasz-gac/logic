package com.tgac.logic.goals;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.debug.DebugStore;
import com.tgac.logic.debug.Trace;
import com.tgac.logic.unification.Package;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public
class NamedGoal implements Goal {
	String name;
	Goal goal;

	@Override
	public Cont<com.tgac.logic.unification.Package, Nothing> apply(Package aPackage) {
		return DebugStore.from(aPackage)
				.map(store -> Trace.tracedCont(name, goal, store.getTracer(),
						aPackage.putStore(store.push(name)),
						answer -> answer.putStore(store)))
				.getOrElse(() -> goal.apply(aPackage));
	}

	@Override
	public String toString() {
		return name;
	}
}
