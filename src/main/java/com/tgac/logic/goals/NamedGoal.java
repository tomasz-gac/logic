package com.tgac.logic.goals;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.fibers.interpreter.OriginCapture;
import com.tgac.logic.debug.DebugStore;
import com.tgac.logic.debug.ProfilerStore;
import com.tgac.logic.debug.Trace;
import com.tgac.logic.goals.optimizer.Optimizer;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public
class NamedGoal implements Goal {
	Function<Package, String> label;
	Goal goal;
	/** The static name when {@code named(String)} minted this; null for rendered labels. */
	String name;
	/**
	 * The mint site when {@link OriginCapture} is on — NamedGoal is to goals
	 * what Scope is to workforces: a boundary that records where it was
	 * minted, under the same substrate switch.
	 */
	@EqualsAndHashCode.Exclude
	Throwable origin = OriginCapture.enabled() ? new Throwable("named at") : null;

	@Override
	public Cont<Package, Nothing> apply(Package aPackage) {
		Cont<Package, Nothing> cont = DebugStore.from(aPackage)
				.map(store -> Trace.tracedCont(label, goal, store.getTracer(),
						aPackage.putStore(store.push(label.apply(aPackage))),
						answer -> answer.putStore(store)))
				.getOrElse(() -> goal.apply(aPackage));
		return ProfilerStore.from(aPackage)
				.<Cont<Package, Nothing>> map(store -> k -> Fiber.named(
						applySite -> store.label(name, label.getClass(), origin,
								() -> label.apply(Package.empty())),
						cont.apply(k)))
				.getOrElse(cont);
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
