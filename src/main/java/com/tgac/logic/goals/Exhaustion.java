package com.tgac.logic.goals;

// ABOUTME: Runs a sub-search as its own workforce and completes at its seal —
// ABOUTME: the exhaustion certificate committed choice, folds and tracing consume.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.interpreter.Scope;
import com.tgac.functional.monad.Cont;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The honest "this sub-search is exhausted": claim the exploration as a fresh
 * workforce and await its seal. Sound under suspension — a sub-search that
 * parks at a tabled entry keeps the workforce open until the entry seals, so
 * the continuation reads a complete answer set, never a partial one. (Fork
 * completion cannot certify this: a fork is a control scatter and promises
 * nothing about its children — docs/design/emit.md in functional.)
 */
public final class Exhaustion {

	private Exhaustion() {
	}

	public static Fiber<Nothing> exhausted(Fiber<Nothing> exploration) {
		Scope sub = Scope.scope();
		return Fiber.claim(sub, exploration)
				.flatMap(__ -> Fiber.sealed(sub));
	}

	/**
	 * Grounds an exploration whose runs deliver values and fail by silence —
	 * the goals protocol — to its COMPLETE delivery set, in arrival order.
	 * This is the ONE lawful way to extract a Cont's deliveries:
	 * {@code Cont<T,R> -> List<T>} is not a continuation operation (the type
	 * has no completion signal, and a multi-shot continuation's invocations
	 * are not enumerable from the algebra); it is a WORKFORCE operation,
	 * legal only under the claim and its seal. Side-channel capture through a
	 * bare apply is exactly the dishonesty this class exists to prevent —
	 * ground here, or compose.
	 *
	 * <p>A delivery after the seal — a smuggled {@code Resume} invoked from
	 * outside the claimed workforce — refuses loudly instead of silently
	 * missing the snapshot.
	 */
	public static <T> Fiber<List<T>> collected(Cont<T, Nothing> exploration) {
		Queue<T> delivered = new ConcurrentLinkedQueue<>();
		Scope sub = Scope.scope();
		return Fiber.claim(sub, exploration.apply(value -> {
					if (sub.isSealed()) {
						throw new IllegalStateException(
								"delivery after the seal: a continuation escaped its claimed workforce — "
										+ value);
					}
					delivered.add(value);
					return Fiber.done(Nothing.nothing());
				}))
				.flatMap(explored -> Fiber.sealed(sub))
				.map(sealed -> new ArrayList<>(delivered));
	}
}
