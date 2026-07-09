package com.tgac.logic.goals;

// ABOUTME: The base optimizer: bottom-up structural recursion that flattens nested
// ABOUTME: conjunctions and disjunctions; NamedGoal is transparent, everything else a leaf.

import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.fibers.Fiber;
import java.util.stream.Stream;

/**
 * Normalizes a goal tree in one bottom-up pass: children first, then nested
 * {@link Conjunction}s splice into their parent and nested {@link Conde}s
 * become sibling alternatives. Nothing nested survives a single traversal, so
 * no fixpoint iteration is needed — the recursion is the termination argument.
 * Subclasses hook per-node work (e.g. reordering owned goals) by overriding a
 * visit method and delegating here for the structural part.
 */
public class CascadingOptimizer implements Optimizer {

	@Override
	public Fiber<Goal> visit(Goal goal) {
		return done(goal);
	}

	@Override
	public Fiber<Goal> visit(Conjunction conjunction) {
		return conjunction.getClauses().stream()
				.map(g -> Fiber.defer(() -> g.accept(this)))
				.map(f -> f.map(g -> g instanceof Conjunction ?
						((Conjunction) g).getClauses().stream() :
						Stream.of(g)))
				.reduce((l, r) -> Fiber.zip(l, r)
						.map(t -> t.apply(Stream::concat)))
				.map(f -> f.map(s -> s.toArray(Goal[]::new))
						.map(gs -> (Goal) new Conjunction().and(gs)))
				.orElseGet(() -> done(Goal.success()));
	}

	@Override
	public Fiber<Goal> visit(Conde conde) {
		return conde.getClauses().stream()
				.map(g -> Fiber.defer(() -> g.accept(this)))
				.map(f -> f.map(g -> g instanceof Conde ?
						((Conde) g).getClauses().stream() :
						Stream.of(g)))
				.reduce((l, r) -> Fiber.zip(l, r)
						.map(t -> t.apply(Stream::concat)))
				.map(f -> f.map(s -> {
					// one alternative at a time: the varargs or() would wrap
					// several goals into a conjunction (conde clause syntax)
					Conde flat = new Conde();
					s.forEach(flat::or);
					return (Goal) flat;
				}))
				.orElseGet(() -> done(Goal.failure()));
	}

	@Override
	public Fiber<Goal> visit(NamedGoal named) {
		// transparent: tracing must not disable optimization
		return named.getGoal().accept(this)
				.map(g -> NamedGoal.of(named.getLabel(), g));
	}

	@Override
	public Fiber<Goal> visit(Barrier barrier) {
		return done(barrier);
	}
}
