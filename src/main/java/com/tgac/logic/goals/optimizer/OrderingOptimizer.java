package com.tgac.logic.goals.optimizer;

// ABOUTME: Sorts barrier-delimited conjunction segments by ascending order (max
// ABOUTME: answers), pricing and rebuilding the tree in one bottom-up traversal.

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Conde;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.NamedGoal;
import com.tgac.logic.goals.Package;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * The one rule of the narrowing/widening taxonomy (docs/design/optimizer.md
 * §3): sort each segment by order, ascending; ∞ last; barriers hold position.
 * Leaves declare via {@link Bounded}; combinators derive (saturating × over
 * conjunction, + over disjunction); everything unrecognised is ∞ — a barrier.
 * Pricing rides the rebuild: the private recursion returns (goal, order)
 * pairs, so each node is priced exactly once and the pairs die with the walk.
 * The substitution is pass state — empty at the root rewrite, live at the
 * defer hook — so the pass is half-blind: sighted at layer boundaries, blind
 * to midway bindings within a layer.
 */
public class OrderingOptimizer extends CascadingOptimizer {

	private final Package bound;

	public OrderingOptimizer() {
		this(Package.empty());
	}

	private OrderingOptimizer(Package bound) {
		this.bound = bound;
	}

	@Override
	public Optimizer with(Package p) {
		return new OrderingOptimizer(p);
	}

	@Override
	public Fiber<Goal> visit(Conjunction conjunction) {
		return price(conjunction).map(Priced::getGoal);
	}

	@Value
	private static class Priced {
		Goal goal;
		long order;
	}

	private Fiber<Priced> price(Goal g) {
		if (g instanceof Conjunction) {
			return priceAll(((Conjunction) g).getClauses())
					.map(ps -> new Priced(
							Conjunction.of(sortSegments(ps).toArray(new Goal[0])),
							productOf(ps)));
		}
		if (g instanceof Conde) {
			return priceAll(((Conde) g).getClauses())
					.map(ps -> {
						List<Goal> alternatives = new ArrayList<>();
						ps.forEach(p -> alternatives.add(p.getGoal()));
						return new Priced(Conde.of(alternatives), sumOf(ps));
					});
		}
		if (g instanceof NamedGoal) {
			NamedGoal named = (NamedGoal) g;
			return Fiber.defer(() -> price(named.getGoal()))
					.map(p -> new Priced(NamedGoal.of(named.getLabel(), p.getGoal()), p.getOrder()));
		}
		if (g instanceof Bounded) {
			return Fiber.done(new Priced(g, ((Bounded) g).answers(bound)));
		}
		return Fiber.done(new Priced(g, Long.MAX_VALUE));
	}

	private Fiber<List<Priced>> priceAll(List<Goal> clauses) {
		Fiber<List<Priced>> acc = Fiber.done(new ArrayList<>());
		for (Goal g : clauses) {
			Fiber<Priced> priced = Fiber.defer(() -> price(g));
			acc = Fiber.zip(acc, priced).map(t -> {
				t._1.add(t._2);
				return t._1;
			});
		}
		return acc;
	}

	/** Barriers (∞) hold position; each maximal finite run sorts ascending, stably. */
	private static List<Goal> sortSegments(List<Priced> ps) {
		List<Goal> out = new ArrayList<>();
		List<Priced> run = new ArrayList<>();
		for (Priced p : ps) {
			if (p.getOrder() == Long.MAX_VALUE) {
				flush(run, out);
				out.add(p.getGoal());
			} else {
				run.add(p);
			}
		}
		flush(run, out);
		return out;
	}

	private static void flush(List<Priced> run, List<Goal> out) {
		run.sort(Comparator.comparingLong(Priced::getOrder));
		run.forEach(p -> out.add(p.getGoal()));
		run.clear();
	}

	private static long productOf(List<Priced> ps) {
		long order = Semirings.SATURATING.one();
		for (Priced p : ps) {
			order = Semirings.SATURATING.times(order, p.getOrder());
		}
		return order;
	}

	private static long sumOf(List<Priced> ps) {
		long order = Semirings.SATURATING.zero();
		for (Priced p : ps) {
			order = Semirings.SATURATING.plus(order, p.getOrder());
		}
		return order;
	}
}
