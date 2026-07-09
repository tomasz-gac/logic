package com.tgac.logic.projection;

// ABOUTME: Projection as a facade: a kernel suspension that waits for deep-groundness
// ABOUTME: and runs the projected goal with the walked value.

import com.tgac.logic.ckanren.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Function2;
import io.vavr.Function3;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProjectionConstraints {

	/**
	 * Parks a suspension: wait until {@code x} is deep-ground, then run
	 * {@code f} with the walked value (docs/design/constraint-kernel.md). The body
	 * splices through the run lane after the pass that grounded {@code x}
	 * quiesces — or runs inline when {@code x} is already ground here.
	 */
	public static <T> Goal project(Unifiable<T> x, Function<T, Goal> f) {
		return Propagation.suspend(
				Collections.singletonList(x),
				sub -> sub.isGround(x),
				s -> f.apply((T) MiniKanren.walkAll(s, x).get().get()).apply(s));
	}

	/** Two-variable projection, watched jointly. */
	public static <T1, T2> Goal project(Unifiable<T1> v1, Unifiable<T2> v2,
			Function2<T1, T2, Goal> f) {
		return Propagation.suspend(
				Arrays.asList(v1, v2),
				sub -> sub.isGround(v1) && sub.isGround(v2),
				s -> f.apply(
						s.substitution().walkAll(v1).get(),
						s.substitution().walkAll(v2).get()).apply(s));
	}

	/** Three-variable projection, watched jointly. */
	public static <T1, T2, T3> Goal project(Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3,
			Function3<T1, T2, T3, Goal> f) {
		return Propagation.suspend(
				Arrays.asList(v1, v2, v3),
				sub -> sub.isGround(v1) && sub.isGround(v2) && sub.isGround(v3),
				s -> f.apply(
						s.substitution().walkAll(v1).get(),
						s.substitution().walkAll(v2).get(),
						s.substitution().walkAll(v3).get()).apply(s));
	}
}
