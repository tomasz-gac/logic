package com.tgac.logic.goals;

// ABOUTME: Runs a sub-search as its own workforce and completes at its seal —
// ABOUTME: the exhaustion certificate committed choice, folds and tracing consume.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.schedulers.Scope;

/**
 * The honest "this sub-search is exhausted": plant the exploration as a fresh
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
		return Fiber.plant(sub, exploration)
				.flatMap(__ -> Fiber.drained(sub));
	}
}
