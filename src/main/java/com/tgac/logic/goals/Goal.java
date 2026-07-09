package com.tgac.logic.goals;

import com.tgac.logic.goals.optimizer.Optimizer;
import com.tgac.logic.goals.optimizer.OptimizerStore;
import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.Scheduler;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.functional.fibers.schedulers.DepthFirstScheduler;
import com.tgac.functional.fibers.schedulers.ForkJoinScheduler;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.debug.DebugStore;
import com.tgac.logic.debug.Trace;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import java.util.Deque;
import java.util.Spliterator;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents a goal in a logic programming system.
 * <pre>
 * A goal is essentially a function that takes a state ({@link Package}, representing
 * substitutions and variable bindings) and returns a continuation ({@link Cont}).
 * This continuation, when run, will produce zero or more resulting states if the goal
 * is satisfied, or indicate failure.
 *
 * Goals can be combined using logical operators like AND (conjunction) and OR (disjunction)
 * to form more complex goals. They can be solved to find instantiations of variables
 * that satisfy the logical conditions.
 *
 * This interface provides factory methods for common goals (e.g., {@link #success()}, {@link #failure()}),
 * combinators (e.g., {@link #and(Goal...)}, {@link #or(Goal...)}), and methods to execute
 * the goal and retrieve solutions (e.g., {@link #solve(Unifiable)}).
 * </pre>
 *
 * @author TGa
 */
public interface Goal extends Function<Package, Cont<Package, Nothing>> {

	/**
	 * A static factory method that simply returns the provided goal.
	 * <pre>
	 * This can be useful for type inference or when a Goal instance is needed
	 * from a lambda or method reference that already produces one.
	 * </pre>
	 *
	 * @param g The goal to return.
	 * @return The same goal {@code g}.
	 */
	static Goal goal(Goal g) {
		return g;
	}

	/**
	 * Creates a new goal representing the logical conjunction (AND) of this goal
	 * and the provided goals.
	 * <pre>
	 * The resulting goal succeeds if and only if this goal and all specified {@code goals} succeed.
	 * </pre>
	 *
	 * @param goals Additional goals to conjunctively combine with this goal.
	 * @return A new {@link Goal} representing the conjunction.
	 * @see Conjunction#and(Goal...)
	 */
	default Goal and(Goal... goals) {
		return new Conjunction().and(this).and(goals);
	}

	/**
	 * Creates a new goal representing the logical disjunction (OR) of this goal
	 * and the provided goals.
	 * <pre>
	 * The resulting goal succeeds if this goal or any of the specified {@code goals} succeed.
	 * This typically implements a fair disjunction (like {@code conde}).
	 * </pre>
	 *
	 * @param goals Additional goals to disjunctively combine with this goal.
	 * @return A new {@link Goal} representing the disjunction.
	 * @see Conde#or(Goal...)
	 * @see #conde(Goal...)
	 */
	default Goal or(Goal... goals) {
		return new Conde().or(this).or(goals);
	}

	/**
	 * Creates a new goal that tries this goal first, and if it fails, tries the
	 * specified {@code goals} in order.
	 * <pre>
	 * This is often used for committed choice or if-then-else like constructs.
	 * This uses {@link Condu} for its underlying mechanism, specifically its {@code orElse} method.
	 * </pre>
	 *
	 * @param goals Alternative goals to try if the preceding ones fail.
	 * @return A new {@link Goal} representing the ordered disjunction.
	 * @see Condu#orElse(Goal...)
	 * @see #condu(Goal...)
	 */
	default Goal orElse(Goal... goals) {
		return new Condu().orElse(this).orElse(goals);
	}

	/**
	 * Creates a new goal that chains this goal with subsequent {@code goals} using a
	 * "first-match" or "committed-choice" strategy, as provided by {@link Condu#orElseFirst(Goal...)}.
	 * <pre>
	 * It attempts goals in sequence, and the behavior regarding commitment to the first
	 * successful path is determined by the {@code Conda} implementation's {@code orElseFirst} method.
	 * </pre>
	 *
	 * @param goals Alternative goals to try, subject to the "orElseFirst" semantics of {@link Condu}.
	 * @return A new {@link Goal} based on {@link Conda#orElseFirst(Goal...)}.
	 * @see Conda#orElseFirst(Goal...)
	 * @see #conda(Goal...)
	 */
	default Goal orElseFirst(Goal... goals) {
		return new Conda().orElseFirst(this).orElseFirst(goals);
	}

	/**
	 * Creates a goal representing a fair disjunction (logical OR) of the provided goals.
	 * <pre>
	 * All branches that lead to success are explored.
	 * If no goals are provided, it results in a {@link #failure()} goal.
	 * This is constructed by reducing the goals using {@link Goal#or(Goal...)}.
	 * </pre>
	 *
	 * @param goals The goals to be combined disjunctively.
	 * @return A new {@link Goal} representing the fair disjunction (conde).
	 */
	static Goal conde(Goal... goals) {
		return Arrays.stream(goals)
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
	}

	/**
	 * Creates a goal representing a committed choice disjunction of the provided goals.
	 * <pre>
	 * It tries goals in order and typically commits to the first one (or set of them)
	 * that leads to a solution, based on the behavior of {@link Goal#orElse(Goal...)}.
	 * If no goals are provided, it results in a {@link #failure()} goal.
	 * </pre>
	 *
	 * @param goals The goals to be combined.
	 * @return A new {@link Goal} representing the committed choice disjunction (condu).
	 */
	static Goal condu(Goal... goals) {
		return Arrays.stream(goals)
				.reduce(Goal::orElse)
				.orElseGet(Goal::failure);
	}

	/**
	 * Creates a goal representing a conditional disjunction, often used for if-then-else
	 * style logic or "guarded" clauses.
	 * <pre>
	 * It combines the provided goals using the {@link Goal#orElseFirst(Goal...)} strategy.
	 * This means it will try to satisfy the goals in a sequence, and the overall behavior
	 * (e.g., committing to the first successful clause) is determined by the
	 * {@code orElseFirst} logic.
	 * If no goals are provided, it results in a {@link #failure()} goal.
	 * </pre>
	 *
	 * @param goals The goals, often structured as condition-consequence clauses, to be combined.
	 * @return A new {@link Goal} representing the conditional disjunction (conda).
	 */
	static Goal conda(Goal... goals) {
		return Arrays.stream(goals)
				.reduce(Goal::orElseFirst)
				.orElseGet(Goal::failure);
	}

	/**
	 * Creates a goal representing the logical conjunction (AND) of all provided goals.
	 * <pre>
	 * The resulting goal succeeds if and only if all specified {@code goals} succeed.
	 * </pre>
	 *
	 * @param goals The goals to be combined conjunctively.
	 * @return A new {@link Goal} representing the conjunction.
	 */
	static Goal all(Goal... goals) {
		return new Conjunction().and(goals);
	}

	/**
	 * Assigns a name to this goal.
	 * <pre>
	 * Useful for debugging and tracing goal execution.
	 * </pre>
	 *
	 * @param name The name to assign to this goal.
	 * @return A {@link NamedGoal} wrapping this goal with the given name.
	 */
	default Goal named(String name) {
		return NamedGoal.of(pkg -> name, this);
	}

	/**
	 * Names this goal with a label rendered against the current state, so a
	 * trace can show the goal's arguments walked to their bindings at each port.
	 *
	 * @param label Renders the goal's label from the package it is applied to.
	 * @return A {@link NamedGoal} whose label is computed per port.
	 */
	default Goal named(Function<Package, String> label) {
		return NamedGoal.of(label, this);
	}

	/**
	 * Dispatch into an {@link Optimizer}. Combinators override this to route to
	 * their own overload; everything else — including plain lambda goals — lands
	 * in the generic {@code visit(Goal)} and is a barrier by construction.
	 */
	default Fiber<Goal> accept(Optimizer optimizer) {
		return optimizer.visit(this);
	}


	/**
	 * Defers the creation of a goal, typically used for defining recursive goals.
	 * <pre>
	 * The supplier function {@code g} is only called when the goal is applied,
	 * preventing infinite recursion during goal construction.
	 * The deferred goal is automatically named "recursive call".
	 * </pre>
	 *
	 * @param g A {@link Supplier} that provides the goal to be executed.
	 * @return A new {@link Goal} that defers the creation of the actual goal.
	 */
	static Goal defer(Supplier<Goal> g) {
		return goal(s -> OptimizerStore.from(s)
				.map(store -> Cont.<Package, Nothing> defer(() ->
						store.rewrite(g.get(), s.substitution())
								.map(body -> body.apply(s))))
				.getOrElse(() -> g.get().apply(s)))
				.named("recursive call");
	}

	/**
	 * Creates a goal that succeeds if the given boolean condition is true,
	 * and fails otherwise.
	 *
	 * @param bool The boolean condition.
	 * @return {@link #success()} if {@code bool} is true, {@link #failure()} otherwise.
	 */
	static Goal successIf(boolean bool) {
		return bool ?
				success() :
				failure();
	}

	/**
	 * Creates a goal that always succeeds.
	 * <pre>
	 * When applied, it returns a continuation that yields the input state unchanged.
	 * It is named "success".
	 * </pre>
	 *
	 * @return A {@link Goal} that always succeeds.
	 */
	static Goal success() {
		return goal(Cont::just)
				.named("success");
	}

	/**
	 * Creates a goal that always fails.
	 * <pre>
	 * When applied, it returns a continuation that yields no results.
	 * It is named "failure".
	 * </pre>
	 *
	 * @return A {@link Goal} that always fails.
	 */
	static Goal failure() {
		return goal(s -> k -> done(nothing()))
				.named("failure");
	}

	/**
	 * Solves this goal, attempting to find instantiations for the specified output variable.
	 * <pre>
	 * This method drives the logic programming computation and produces a stream of results.
	 *
	 * The process involves:
	 * - Applying the current goal to an empty initial state ({@link Package#empty()}).
	 * - For each successful resulting state, reifying (extracting the value of) the {@code out} variable.
	 * - Using an {@link Scheduler} (provided by the {@code factory}) to manage the execution of the continuations.
	 * - Streaming the reified values of {@code out} as they are found.
	 *
	 * The results are provided as a {@link Stream}. The stream is populated lazily as the engine
	 * finds solutions. The engine is closed when the stream is closed.
	 * The {@link Spliterator} implementation detail: if the engine completes its computation,
	 * its {@code tryAdvance} method may process all remaining buffered results by calling the
	 * consumer multiple times before returning false. Standard {@code Spliterator.tryAdvance}
	 * typically processes at most one element per call.
	 * </pre>
	 *
	 * @param <T> The type of the value held by the output unifiable variable.
	 * @param out The {@link Unifiable} variable whose instantiated values are desired.
	 * @param factory A function that takes a {@link Fiber} computation (representing the goal's execution logic)
	 * 		and produces an {@link Scheduler} to run it. This allows for different execution
	 * 		strategies (e.g., BFS, DFS, parallel).
	 * @return A {@link Stream} of {@link Unifiable}s, where each element is an instantiation
	 * 		of the {@code out} variable representing a solution.
	 */
	default <T> Stream<Reified<T>> solve(
			Unifiable<T> out,
			Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		return solveFrom(Package.empty().withStore(Table.empty()), out, factory);
	}

	/**
	 * Solves this goal while reporting box-model ports through the given tracer.
	 * Seeds a {@link DebugStore} so every {@link NamedGoal} reached during the
	 * search reports its Call/Exit/Redo/Fail ports.
	 */
	default <T> Stream<Reified<T>> solve(Unifiable<T> out, Trace.Tracer tracer) {
		// depth-first so the trace reads in Prolog order: a branch runs to completion
		// before its siblings, rather than interleaving the ports of concurrent branches.
		return solveFrom(
				Package.empty().withStore(Table.empty()).withStore(DebugStore.of(tracer)),
				out, DepthFirstScheduler::of);
	}

	/**
	 * Solves this goal while printing a box-model trace to standard out.
	 * Convenience for {@code solve(out, Trace.printing())}; consume the returned
	 * stream to drive the search. Use {@link Trace#spy} or {@link Trace.Tracer#filter}
	 * with {@link #solve(Unifiable, Trace.Tracer)} to focus the trace.
	 */
	default <T> Stream<Reified<T>> trace(Unifiable<T> out) {
		return solve(out, Trace.printing());
	}

	default <T> Stream<Reified<T>> solveFrom(
			Package root,
			Unifiable<T> out,
			Function<Fiber<Nothing>, Scheduler<Nothing>> factory) {
		Deque<Reified<T>> results = new LinkedBlockingDeque<>();

		Fiber<Nothing> recur = apply(root)
				.flatMap(s -> Constraints.reify(s, out))
				.run(v -> {
					results.add(v);      // Push result to queue
					return nothing();         // Unit signal
				});
		Scheduler<Nothing> scheduler = factory.apply(recur);

		Spliterator<Reified<T>> spliterator = new Spliterator<Reified<T>>() {
			@Override
			public boolean tryAdvance(Consumer<? super Reified<T>> action) {
				while (results.isEmpty()) { // Loop if no results are immediately available
					// Run the engine for a batch of steps.
					// engine.run() returns true if the entire computation has completed.
					if (scheduler.run(64, v -> { /* Engine's internal step callback, not for results here */ })) {
						// Engine has completed. Process any remaining items in the results queue.
						// Note: This inner loop processes all remaining items in one go if the engine is done.
						// This differs from typical tryAdvance behavior which processes one item.
						while (!results.isEmpty()) {
							action.accept(results.poll());
						}
						return false; // Signal that the spliterator is exhausted as engine completed.
					}
					// If engine has not completed, but results became available, exit loop to process one.
					if (!results.isEmpty()) {
						break;
					}
				}
				action.accept(results.poll());
				return true; // One item processed
			}

			@Override
			public Spliterator<Reified<T>> trySplit() {
				return null; // Splitting not supported
			}

			@Override
			public long estimateSize() {
				return Long.MAX_VALUE; // Size is unknown
			}

			@Override
			public int characteristics() {
				return Spliterator.ORDERED | Spliterator.NONNULL;
			}
		};

		return StreamSupport.stream(spliterator, false)
				.onClose(() -> {
					try {
						scheduler.close();
					} catch (Exception e) {
						throw new RuntimeException("Failed to close engine", e);
					}
				});
	}

	/**
	 * Solves this goal in parallel, attempting to find instantiations for the specified output variable.
	 * <pre>
	 * This is a convenience method that uses a {@link ForkJoinScheduler} on the common pool.
	 * </pre>
	 *
	 * @param <T> The type of the value held by the output unifiable variable.
	 * @param out The {@link Unifiable} variable whose instantiated values are desired.
	 * @return A {@link Stream} of {@link Unifiable}s representing solutions, potentially computed in parallel.
	 * @see #solve(Unifiable, Function)
	 * @see ForkJoinScheduler
	 */
	default <T> Stream<Reified<T>> solveParallel(Unifiable<T> out) {
		return solve(out, ForkJoinScheduler::new);
	}

	/**
	 * Solves this goal, attempting to find instantiations for the specified output variable,
	 * using a default Breadth-First Search (BFS) engine.
	 * <pre>
	 * This is a convenience method that uses a {@link BreadthFirstScheduler}.
	 * </pre>
	 *
	 * @param <T> The type of the value held by the output unifiable variable.
	 * @param out The {@link Unifiable} variable whose instantiated values are desired.
	 * @return A {@link Stream} of {@link Unifiable}s representing solutions, computed using BFS.
	 * @see #solve(Unifiable, Function)
	 * @see BreadthFirstScheduler
	 */
	default <T> Stream<Reified<T>> solve(Unifiable<T> out) {
		return solve(out, BreadthFirstScheduler::new);
	}

	/**
	 * Solve with an ambient optimizer: the root tree is rewritten once against
	 * the initial substitution (the static tier), and the store rides the
	 * package so each recursion layer is rewritten as it unfolds at the
	 * {@link #defer} hook (docs/design/ambient-optimizer.md).
	 */
	default <T> Stream<Reified<T>> solve(Unifiable<T> out, Optimizer optimizer) {
		return accept(optimizer).get()
				.solveFrom(Package.empty().putStore(OptimizerStore.of(optimizer)),
						out, BreadthFirstScheduler::new);
	}

}