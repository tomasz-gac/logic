package com.tgac.logic.projection;

// ABOUTME: Projection as a facade: a kernel suspension that waits for deep-groundness
// ABOUTME: and runs the projected goal with the walked value.

import com.tgac.logic.ckanren.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import java.util.Collections;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProjectionConstraints {

	/**
	 * Parks a suspension: wait until {@code x} is deep-ground, then run
	 * {@code f} with the walked value (docs/design/suspensions.md §5). The body
	 * splices through the run lane after the pass that grounded {@code x}
	 * quiesces — or runs inline when {@code x} is already ground here.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Goal project(Unifiable<T> x, Function<T, Goal> f) {
		return Propagation.suspend(
				Collections.singletonList(x),
				sub -> sub.isGround(x),
				s -> f.apply((T) MiniKanren.walkAll(s, x).get().get()).apply(s));
	}
}
