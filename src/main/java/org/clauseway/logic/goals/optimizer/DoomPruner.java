package org.clauseway.logic.goals.optimizer;

// ABOUTME: Prunes doomed branches: a posting refuted under the pass state rewrites
// ABOUTME: to failure, a dead conjunct collapses its conjunction, dead alternatives drop.

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.goals.Conde;
import org.clauseway.logic.goals.Conjunction;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.NamedGoal;
import org.clauseway.logic.goals.Package;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * The refutation consumer of {@link Posting#doomed}: a leaf provably failing
 * under the pass state rewrites to {@link Goal#failure()}, and death folds
 * structurally — a dead conjunct kills its whole conjunction, a dead
 * alternative drops from its disjunction (all dead = failure). Doom is a
 * trust surface (never claimed when later knowledge could lift it), so an
 * open posting is left untouched. The kill needs no ordering: pruning and
 * {@link OrderingOptimizer} are separate passes, composed via
 * {@link Optimizer#pipeline}. The package is pass state — empty at the root
 * rewrite, live at the defer hook — so kills sharpen as knowledge arrives.
 */
public class DoomPruner extends CascadingOptimizer {

	private final Package bound;

	public DoomPruner() {
		this(Package.empty());
	}

	private DoomPruner(Package bound) {
		this.bound = bound;
	}

	@Override
	public Optimizer with(Package p) {
		return new DoomPruner(p);
	}

	@Override
	public Fiber<Goal> visit(Goal goal) {
		return prune(goal).map(Pruned::getGoal);
	}

	@Override
	public Fiber<Goal> visit(Conjunction conjunction) {
		return prune(conjunction).map(Pruned::getGoal);
	}

	@Override
	public Fiber<Goal> visit(Conde conde) {
		return prune(conde).map(Pruned::getGoal);
	}

	@Override
	public Fiber<Goal> visit(NamedGoal named) {
		return prune(named).map(Pruned::getGoal);
	}

	@Value
	private static class Pruned {
		Goal goal;
		boolean dead;
	}

	private Fiber<Pruned> prune(Goal g) {
		if (g instanceof Conjunction) {
			return visitAll(((Conjunction) g).getClauses(), this::prune)
					.map(ps -> ps.stream().anyMatch(Pruned::isDead) ?
							new Pruned(Goal.failure(), true) :
							new Pruned(Conjunction.of(ps.stream()
									.map(Pruned::getGoal)
									.toArray(Goal[]::new)), false));
		}
		if (g instanceof Conde) {
			return visitAll(((Conde) g).getClauses(), this::prune)
					.map(ps -> {
						List<Goal> live = ps.stream()
								.filter(p -> !p.isDead())
								.map(Pruned::getGoal)
								.collect(Collectors.toList());
						return live.isEmpty() ?
								new Pruned(Goal.failure(), true) :
								new Pruned(Conde.of(live), false);
					});
		}
		if (g instanceof NamedGoal) {
			NamedGoal named = (NamedGoal) g;
			return Fiber.defer(() -> prune(named.getGoal()))
					.map(p -> new Pruned(
							NamedGoal.of(named.getLabel(), p.getGoal(), named.getName()),
							p.isDead()));
		}
		if (g instanceof Posting && ((Posting) g).doomed(bound)) {
			return Fiber.done(new Pruned(Goal.failure(), true));
		}
		return Fiber.done(new Pruned(g, false));
	}
}
