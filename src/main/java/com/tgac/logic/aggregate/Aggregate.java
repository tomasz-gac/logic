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
import io.vavr.Tuple;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Watermark;
import com.tgac.logic.goals.optimizer.Barrier;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Aggregation over the solutions of a CLOSED sub-goal. Each construct hands
 * its body a fresh template variable born inside the aggregate's scope, runs
 * the goal the body builds to exhaustion, folds the answers, and succeeds
 * exactly once with the result (except {@link #max}/{@link #min}, which fail
 * on an empty solution set). The enclosed goal's variables do not leak:
 * collected answers are copied.
 *
 * A closed sub-goal is a self-contained program: it may consume ground
 * values from the surrounding search (the walk dissolves a bound variable
 * into its value), but a variable born before the aggregate surfacing inside
 * the sub-solve refuses loudly, by name — the {@link Watermark}. Otherwise
 * the fold's scalar would silently depend on knowledge the surrounding
 * search can still grow.
 *
 * Every aggregate is a BARRIER: its answer is a fold over the sub-search AS
 * RUN FROM THE BINDINGS AT ITS POSITION, so moving it changes the question,
 * not the plan — the optimizer's more-bound-implies-subset theorem does not
 * cover it (the same stratification rule as Datalog's for aggregation).
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
	 * Collect a copy of the template for every solution of the goal
	 * {@code body} builds, in the order the scheduler produces them.
	 */
	public static <T> Goal findall(Function<Unifiable<T>, Goal> body, Unifiable<LList<T>> result) {
		return closedAggregate(body, (template, goal) -> findall(template, goal, result));
	}

	/**
	 * Count the DISTINCT solutions of the goal {@code body} builds — the
	 * answer set's size, not the delivery stream's length: a solution proven
	 * several ways counts once (alpha-equivalence on the reified template).
	 */
	public static <T> Goal count(Function<Unifiable<T>, Goal> body, Unifiable<Integer> result) {
		return closedAggregate(body, (template, goal) -> count(template, goal, result));
	}

	/**
	 * Sum the template over the DISTINCT solutions of the goal {@code body}
	 * builds (0 if none). The template is both the solution identity and the
	 * payload; to sum a payload over solutions with a richer identity, use
	 * {@link #sum(BiFunction, Unifiable)}.
	 */
	public static Goal sum(Function<Unifiable<Integer>, Goal> body, Unifiable<Integer> result) {
		return closedAggregate(body, (expr, goal) -> foldDistinct(expr, expr, goal, result, Monoids.INT_SUM, false));
	}

	/**
	 * Sum {@code payload} once per DISTINCT (solution, payload) pair of the
	 * goal {@code body} builds (0 if none). The payload is not the solution
	 * identity: distinct solutions carrying equal payloads each contribute.
	 */
	public static <S> Goal sum(BiFunction<Unifiable<S>, Unifiable<Integer>, Goal> body, Unifiable<Integer> result) {
		return Barrier.of((Goal) pkg -> k -> {
			Watermark watermark = Watermark.now();
			Unifiable<S> solution = lvar();
			Unifiable<Integer> payload = lvar();
			Goal closed = pkg2 -> body.apply(solution, payload).apply(pkg2.putStore(watermark));
			return foldDistinct(lval(Tuple.of(solution, payload)), payload, closed, result, Monoids.INT_SUM, false)
					.apply(pkg).apply(k);
		});
	}

	/**
	 * Largest template over the solutions of the goal {@code body} builds;
	 * fails if none.
	 */
	public static Goal max(Function<Unifiable<Integer>, Goal> body, Unifiable<Integer> result) {
		return closedAggregate(body, (expr, goal) -> fold(expr, goal, result, Monoids.INT_MAX, true));
	}

	/**
	 * Smallest template over the solutions of the goal {@code body} builds;
	 * fails if none.
	 */
	public static Goal min(Function<Unifiable<Integer>, Goal> body, Unifiable<Integer> result) {
		return closedAggregate(body, (expr, goal) -> fold(expr, goal, result, Monoids.INT_MIN, true));
	}

	/**
	 * The closed-aggregate frame: at apply, draw the watermark, mint the
	 * template above it, build the body's goal, and run it with the mark
	 * riding its packages. The mark never leaves the sub-solve — answers
	 * are copied.
	 */
	private static <T> Goal closedAggregate(
			Function<Unifiable<T>, Goal> body,
			BiFunction<Unifiable<T>, Goal, Goal> fold) {
		return Barrier.of((Goal) pkg -> k -> {
			Watermark watermark = Watermark.now();
			Unifiable<T> template = lvar();
			Goal closed = pkg2 -> body.apply(template).apply(pkg2.putStore(watermark));
			return fold.apply(template, closed).apply(pkg).apply(k);
		});
	}

	private static <T> Goal findall(Unifiable<T> template, Goal goal, Unifiable<LList<T>> result) {
		return Barrier.of(pkg -> k -> {
			Collection<Reified<T>> collected = new ConcurrentLinkedQueue<>();
			return Exhaustion.exhausted(goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, template).apply(reified -> {
								collected.add(reified);
								return done(nothing());
							})))
					.flatMap(exhausted -> buildList(collected).flatMap(list ->
							Constraints.unify(result, list).apply(pkg).apply(k)));
		});
	}

	private static <T> Goal count(Unifiable<T> template, Goal goal, Unifiable<Integer> result) {
		return Barrier.of((Goal) pkg -> k -> {
			Set<Reified<T>> solutions = ConcurrentHashMap.newKeySet();
			return Exhaustion.exhausted(goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, template).apply(reified -> {
								solutions.add(reified);
								return done(nothing());
							})))
					.flatMap(exhausted -> Constraints.unify(result, lval(solutions.size())).apply(pkg).apply(k));
		});
	}

	/**
	 * Folds {@code payload} once per distinct solution: the reified
	 * {@code identity} keys the answer set, and each key's first delivery
	 * contributes its payload to the monoid. The seen flag keeps "no answers"
	 * distinguishable from a fold that happens to equal the monoid identity.
	 */
	private static Goal foldDistinct(
			Unifiable<?> identity,
			Unifiable<Integer> payload,
			Goal goal,
			Unifiable<Integer> result,
			Monoid<Integer> monoid,
			boolean failWhenEmpty) {
		return Barrier.of((Goal) pkg -> k -> {
			Set<Reified<?>> solutions = ConcurrentHashMap.newKeySet();
			AtomicReference<Integer> acc = new AtomicReference<>(monoid.empty());
			AtomicBoolean seen = new AtomicBoolean(false);
			return Exhaustion.exhausted(goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, identity).apply(id ->
									Constraints.reify(answerPkg, payload).apply(v -> {
										if (solutions.add(id)) {
											seen.set(true);
											acc.updateAndGet(cur -> monoid.combine(cur, requireInt(v)));
										}
										return done(nothing());
									}))))
					.flatMap(exhausted -> {
						if (!seen.get() && failWhenEmpty) {
							return done(nothing());
						}
						return Constraints.unify(result, lval(acc.get())).apply(pkg).apply(k);
					});
		});
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
		return Barrier.of((Goal) pkg -> k -> {
			AtomicReference<Integer> acc = new AtomicReference<>(monoid.empty());
			AtomicBoolean seen = new AtomicBoolean(false);
			return Exhaustion.exhausted(goal.apply(pkg).apply(answerPkg ->
							Constraints.reify(answerPkg, expr).apply(reified -> {
								int v = requireInt(reified);
								seen.set(true);
								acc.updateAndGet(cur -> monoid.combine(cur, v));
								return done(nothing());
							})))
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
