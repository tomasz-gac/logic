package com.tgac.logic.tabling;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Awaitable;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tgac.functional.fibers.Fiber.done;

/**
 * Provides tabling (memoization) for logic goals to prevent infinite loops
 * and improve performance by caching answers.
 *
 * Tabling implements a master/slave pattern where the first invocation of a
 * tabled goal becomes the "master" and executes the goal, caching answers
 * as they are produced. Subsequent invocations with the same arguments
 * become "slaves" that read from the cache.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Tabling {

	/**
	 * Create a tabled version of a goal.
	 *
	 * The goal will only be tabled if its arguments are ground (fully instantiated).
	 * If arguments contain unbound variables, the goal executes normally without tabling.
	 *
	 * @param goalName The name of the goal for identification
	 * @param args The arguments to the goal
	 * @param goalFactory A function that creates the actual goal given the arguments
	 * @return A tabled goal that caches answers
	 */
	public static Goal tabled(String goalName, List<Unifiable> args, java.util.function.Function<List<Unifiable>, Goal> goalFactory) {
		return pkg -> k -> {
			// Create the call, walk it deeply, then check if it contains any LVars (non-ground)
			// Use Fiber to avoid stack overflow on deeply nested structures
			return Call.of(goalName, args).walkFiber(pkg).flatMap(walkedCall -> {
				if (goalName.equals("path")) {
					System.out.println("[DEBUG] path - walked: " + walkedCall);
				}
				return walkedCall.containsLVarsFiber().flatMap(hasLVars -> {
					if (hasLVars) {
						// Contains unbound variables - execute normally without tabling
						if (goalName.equals("path")) {
							System.out.println("[DEBUG] path - BYPASSING");
						}
						return goalFactory.apply(args).apply(pkg).apply(k);
					}

					// Ground call - use tabling
					if (goalName.equals("path")) {
						System.out.println("[DEBUG] path - USING TABLING");
					}
					Table table = Table.instance();
					TableEntry entry = table.getOrCreateEntry(walkedCall);

					// Try to become master
					if (entry.tryBecomeMaster()) {
						// We are the master - execute goal and cache answers
						return producerCont(entry, goalFactory.apply(args), pkg).apply(k);
					} else {
						// We are a slave - consume from cache
						return consumerCont(entry, pkg).apply(k);
					}
				});
			});
		};
	}

	/**
	 * Master producer: executes the goal and caches each answer.
	 * After caching is complete, the master becomes a consumer and reads from the cache.
	 */
	private static Cont<Package, Nothing> producerCont(TableEntry entry, Goal goal, Package initialPkg) {
		return k -> {
			// Execute the goal with a caching-only continuation
			Fiber<Nothing> cachingFiber = goal.apply(initialPkg).apply(ans -> {
				entry.addAnswer(ans);
				// Don't call k here - just cache and continue the goal
				return done(Nothing.nothing());
			});

			// After caching completes, mark as complete and then consume answers like a slave
			return cachingFiber.flatMap(result -> {
				entry.markComplete();

				// Now consume answers from the cache to continue the query
				return consumeAnswers(entry, k, initialPkg, new AtomicInteger(0));
			});
		};
	}

	/**
	 * Slave consumer: reads answers from the cache, suspending when cache is exhausted.
	 */
	private static Cont<Package, Nothing> consumerCont(TableEntry entry, Package initialPkg) {
		return k -> consumeAnswers(entry, k, initialPkg, new AtomicInteger(0));
	}

	/**
	 * Recursively consume answers from the cache.
	 * Suspends when the cache is exhausted and resumes when new answers arrive.
	 *
	 * For ground calls, we return initialPkg (not the cached Package) because
	 * the cached Package contains bindings from a different variable context.
	 * We only need to know that the master succeeded.
	 */
	private static Fiber<Nothing> consumeAnswers(
			TableEntry entry,
			Fiber.Fn<Package, Nothing> k,
			Package initialPkg,
			AtomicInteger index) {

		int currentIndex = index.get();

		// Wait for answer at current index
		CompletableFuture<TableEntry.AnswerStatus> answerFuture = entry.waitForAnswerAt(currentIndex);

		// If already completed, process immediately
		if (answerFuture.isDone()) {
			try {
				TableEntry.AnswerStatus status = answerFuture.get();
				if (status instanceof TableEntry.Done) {
					// Master is done, no more answers
					return done(Nothing.nothing());
				} else if (status instanceof TableEntry.Answer) {
					// Master has an answer - the call succeeded
					// For ground calls, return our own Package, not the master's cached Package
					index.incrementAndGet();
					// Yield our own Package, then continue consuming
					return k.apply(initialPkg).flatMap(__ ->
						Fiber.defer(() -> consumeAnswers(entry, k, initialPkg, index))
					);
				}
			} catch (Exception e) {
				throw new RuntimeException("Error getting answer from table", e);
			}
		}

		// Answer not ready - suspend until it arrives
		Awaitable<TableEntry.AnswerStatus> awaitable = () -> answerFuture;

		return awaitable.await(status -> {
			if (status instanceof TableEntry.Done) {
				// Master is done, no more answers
				return done(Nothing.nothing());
			} else if (status instanceof TableEntry.Answer) {
				// Master has an answer - the call succeeded
				// For ground calls, return our own Package, not the master's cached Package
				index.incrementAndGet();
				// Yield our own Package, then continue consuming
				return k.apply(initialPkg).flatMap(__ ->
					Fiber.defer(() -> consumeAnswers(entry, k, initialPkg, index))
				);
			} else {
				return done(Nothing.nothing());
			}
		});
	}
}
