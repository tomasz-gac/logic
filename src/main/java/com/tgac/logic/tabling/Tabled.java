package com.tgac.logic.tabling;

// ABOUTME: A tabled relation: defined once over formal parameters, applied to actual arguments.
// ABOUTME: apply() wires the cache key and the body from the same tuple, so they cannot diverge.

import com.tgac.logic.goals.Goal;
import java.util.function.Function;

/**
 * A tabled relation. The body is written once, at definition time, over the
 * relation's formal parameters — the argument tuple {@code T}, e.g.
 * {@code Tuple2<Unifiable<A>, Unifiable<B>>}. Each {@link #apply} keys the
 * cache on this relation's identity plus the reified actual arguments, and
 * runs the body over those same arguments; the two cannot diverge because
 * call sites never assemble the pair themselves.
 *
 * The {@code self} parameter of {@link Tabling#defineRecursive} is the recursion
 * handle: a lambda cannot name the variable it is being assigned to.
 */
public final class Tabled<T> {
	private final Function<Tabled<T>, Function<T, Goal>> body;

	Tabled(Function<Tabled<T>, Function<T, Goal>> body) {
		this.body = body;
	}

	/**
	 * Apply the relation to actual arguments. Name the resulting goal with
	 * {@link Goal#named} where traces should be legible.
	 */
	public Goal apply(T args) {
		return Tabling.tabled(this, args, () -> body.apply(this).apply(args));
	}

}
