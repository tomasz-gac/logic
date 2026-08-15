package com.tgac.logic.projection;

// ABOUTME: Projection goals: park a kernel suspension until deep-groundness, then
// ABOUTME: run the body with the walked value. Suspensions are Propagation's own.

import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.optimizer.Bounded;
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
public class Projection {

	/**
	 * Parks a suspension: wait until {@code x} is deep-ground, then run
	 * {@code f} with the walked value (docs/reference/constraint-kernel.md). The body
	 * splices through the run lane after the pass that grounded {@code x}
	 * quiesces — or runs inline when {@code x} is already ground here.
	 */
	public static <T> Goal project(Unifiable<T> x, Function<T, Goal> f) {
		return Bounded.of(1, Propagation.suspend(
				Collections.singletonList(x),
				sub -> sub.isGround(x),
				s -> Cont.defer(() -> MiniKanren.walkAll(s.substitution(), x)
						.map(w -> f.apply((T) w.get()).apply(s)))));
	}

	/** Two-variable projection, watched jointly. */
	public static <T1, T2> Goal project(Unifiable<T1> v1, Unifiable<T2> v2,
			Function2<T1, T2, Goal> f) {
		return Bounded.of(1, Propagation.suspend(
				Arrays.asList(v1, v2),
				sub -> sub.isGround(v1) && sub.isGround(v2),
				s -> Cont.defer(() -> MiniKanren.walkAll(s.substitution(), v1)
						.flatMap(w1 -> MiniKanren.walkAll(s.substitution(), v2)
								.map(w2 -> f.apply(w1.get(), w2.get()).apply(s))))));
	}

	/** Three-variable projection, watched jointly. */
	public static <T1, T2, T3> Goal project(Unifiable<T1> v1, Unifiable<T2> v2, Unifiable<T3> v3,
			Function3<T1, T2, T3, Goal> f) {
		return Bounded.of(1, Propagation.suspend(
				Arrays.asList(v1, v2, v3),
				sub -> sub.isGround(v1) && sub.isGround(v2) && sub.isGround(v3),
				s -> Cont.defer(() -> MiniKanren.walkAll(s.substitution(), v1)
						.flatMap(w1 -> MiniKanren.walkAll(s.substitution(), v2)
								.flatMap(w2 -> MiniKanren.walkAll(s.substitution(), v3)
										.map(w3 -> f.apply(w1.get(), w2.get(), w3.get()).apply(s)))))));
	}
}
