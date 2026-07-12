package com.tgac.logic.aggregate;

// ABOUTME: Reflects a sub-search's solutions into a value — findall and its count/sum/max/min folds.
// ABOUTME: Runs the goal to exhaustion, copies each answer, and yields one result to the continuation.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.Monoid;
import com.tgac.functional.algebra.Monoids;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Aggregation over the solutions of a goal. Each construct runs its goal to
 * exhaustion in the current state, folds the answers, and succeeds exactly
 * once with the result (except {@link #max}/{@link #min}, which fail on an
 * empty solution set). The enclosed goal's variables do not leak: collected
 * answers are copied.
 *
 * Sound when the enclosed goal terminates on its own. Over a tabled recursive
 * goal a consumer's fiber completes by parking before the relation is
 * exhausted, so the fold would see a partial answer set — the same completion
 * caveat as if-then-else over tabled goals. Under a parallel scheduler the
 * fold is order-independent but {@link #findall}'s list order is not.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Aggregate {

	/**
	 * Collect a copy of {@code template} for every solution of {@code goal}
	 * into {@code result}, in the order the scheduler produces them.
	 */
	public static <T> Goal findall(Unifiable<T> template, Goal goal, Unifiable<LList<T>> result) {
		return Bounded.of(1, (Goal) pkg -> k -> {
			Collection<Reified<T>> collected = new ConcurrentLinkedQueue<>();
			return goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, template).apply(reified -> {
								collected.add(reified);
								return done(nothing());
							}))
					.flatMap(exhausted -> buildList(collected).flatMap(list ->
							Constraints.unify(result, list).apply(pkg).apply(k)));
		});
	}

	/**
	 * Count the solutions of {@code goal}.
	 */
	public static Goal count(Goal goal, Unifiable<Integer> result) {
		return Bounded.of(1, (Goal) pkg -> k -> {
			AtomicInteger n = new AtomicInteger(0);
			return goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, lvar()).apply(reified -> {
								n.incrementAndGet();
								return done(nothing());
							}))
					.flatMap(exhausted -> Constraints.unify(result, lval(n.get())).apply(pkg).apply(k));
		});
	}

	/**
	 * Sum {@code expr} over the solutions of {@code goal} (0 if none).
	 */
	public static Goal sum(Unifiable<Integer> expr, Goal goal, Unifiable<Integer> result) {
		return fold(expr, goal, result, Monoids.INT_SUM, false);
	}

	/**
	 * Largest {@code expr} over the solutions of {@code goal}; fails if none.
	 */
	public static Goal max(Unifiable<Integer> expr, Goal goal, Unifiable<Integer> result) {
		return fold(expr, goal, result, Monoids.INT_MAX, true);
	}

	/**
	 * Smallest {@code expr} over the solutions of {@code goal}; fails if none.
	 */
	public static Goal min(Unifiable<Integer> expr, Goal goal, Unifiable<Integer> result) {
		return fold(expr, goal, result, Monoids.INT_MIN, true);
	}

	/**
	 * Folds {@code expr} over the goal's answers through a monoid witness. The
	 * identity is a safe starting accumulator whenever at least one answer
	 * arrives; the seen flag keeps "no answers" distinguishable from a fold
	 * that happens to equal the identity.
	 */
	private static Goal fold(
			Unifiable<Integer> expr,
			Goal goal,
			Unifiable<Integer> result,
			Monoid<Integer> monoid,
			boolean failWhenEmpty) {
		return Bounded.of(1, (Goal) pkg -> k -> {
			AtomicReference<Integer> acc = new AtomicReference<>(monoid.empty());
			AtomicBoolean seen = new AtomicBoolean(false);
			return goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, expr).apply(reified -> {
								int v = requireInt(reified);
								seen.set(true);
								acc.updateAndGet(cur -> monoid.combine(cur, v));
								return done(nothing());
							}))
					.flatMap(exhausted -> {
						if (!seen.get() && failWhenEmpty) {
							return done(nothing());
						}
						return Constraints.unify(result, lval(acc.get())).apply(pkg).apply(k);
					});
		});
	}

	private static <T> Fiber<Unifiable<LList<T>>> buildList(Collection<Reified<T>> collected) {
		List<Reified<T>> snapshot = new ArrayList<>(collected);
		Fiber<ArrayList<Term<T>>> items = done(new ArrayList<>());
		for (Reified<T> reified : snapshot) {
			items = items.flatMap(acc ->
					MiniKanren.instantiate(reified).map(u -> {
						acc.add(u);
						return acc;
					}));
		}
		return items.map(acc -> LList.ofAll(acc.size(), acc::get));
	}

	private static int requireInt(Reified<Integer> reified) {
		if (reified.asReified().isDefined()) {
			throw new IllegalStateException("cannot aggregate over an unbound expression");
		}
		return reified.get();
	}
}
