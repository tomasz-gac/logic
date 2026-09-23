package org.clauseway.logic.goals.optimizer;

// ABOUTME: The normalization pass: nested conjunctions splice into their parent
// ABOUTME: and nested condes become sibling alternatives, in one bottom-up traversal.

import static org.clauseway.functional.fibers.Fiber.done;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.clauseway.functional.fibers.Fiber;
import org.clauseway.logic.goals.Conde;
import org.clauseway.logic.goals.Conjunction;
import org.clauseway.logic.goals.Goal;

/**
 * Normalizes a goal tree in one bottom-up pass: children first, then nested
 * {@link Conjunction}s splice into their parent and nested {@link Conde}s
 * become sibling alternatives. Nothing nested survives a single traversal, so
 * no fixpoint iteration is needed — the recursion is the termination argument.
 * Everything else is the neutral walk inherited from {@link Optimizer}.
 */
public class CascadingOptimizer implements Optimizer {

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
						.map(gs -> (Goal) Conjunction.of(gs)))
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
				.map(f -> f.map(s -> (Goal) Conde.of(s.collect(Collectors.toList()))))
				.orElseGet(() -> done(Goal.failure()));
	}
}
