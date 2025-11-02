package com.tgac.logic.tabling;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Awaitable;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.functional.fibers.MFiber.mdone;

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
	 * Uses Byrd's tabling algorithm:
	 * 1. Reify the arguments to create a lookup key
	 * 2. Look up the call in the table
	 * 3. If new call, become master and execute the goal, caching answer terms
	 * 4. If existing call, become slave and unify with cached answer terms
	 *
	 * @param goalName The name of the goal for identification
	 * @param args The arguments to the goal
	 * @param goalFactory A function that creates the actual goal given the arguments
	 * @return A tabled goal that caches answers
	 */
	public static Goal tabled(String goalName, List<Unifiable> args, java.util.function.Function<List<Unifiable>, Goal> goalFactory) {
		return pkg -> k -> {
			if (goalName.equals("path")) {
				System.out.println("[DEBUG] path - tabling with args: " + args);
			}
			// Step 1: Reify the arguments in the current package to create the lookup key
			return reifyArguments(pkg, args).flatMap(reifiedArgs -> {
				// Step 2: Create a Call with the reified arguments as the key
				Call key = Call.of(goalName, reifiedArgs);
				if (goalName.equals("path")) {
					System.out.println("[DEBUG] path - reified key: " + key);
				}

				// Step 3: Look up or create table entry
				Table table = Table.instance();
				TableEntry entry = table.getOrCreateEntry(key);

				// Step 4: Try to become master
				if (entry.tryBecomeMaster()) {
					if (goalName.equals("path")) {
						System.out.println("[DEBUG] path - MASTER");
					}
					// We are the master
					return producerCont(entry, goalFactory.apply(args), pkg, args).apply(k);
				} else {
					if (goalName.equals("path")) {
						System.out.println("[DEBUG] path - SLAVE");
					}
					// We are a slave
					return consumerCont(entry, pkg, args).apply(k);
				}
			});
		};
	}

	/**
	 * Reify all arguments to create the table lookup key.
	 */
	@SuppressWarnings("unchecked")
	private static Fiber<List<Unifiable>> reifyArguments(Package pkg, List<Unifiable> args) {
		Fiber<List<Unifiable>> result = done(List.empty());
		for (Unifiable arg : args) {
			result = result.flatMap(accum ->
				MiniKanren.reify(pkg, arg).map(reified -> accum.append((Unifiable) reified))
			);
		}
		return result;
	}

	/**
	 * Master producer: executes the goal and caches answer terms.
	 * After caching is complete, the master becomes a consumer and reads from the cache.
	 */
	@SuppressWarnings("unchecked")
	private static Cont<Package, Nothing> producerCont(TableEntry entry, Goal goal, Package initialPkg, List<Unifiable> args) {
		return k -> {
			// Execute the goal with a caching-only continuation
			Fiber<Nothing> cachingFiber = goal.apply(initialPkg).apply(answerPkg -> {
				// For each answer Package, reify the arguments and cache them as answer terms
				return reifyArguments(answerPkg, args).flatMap(answerTerm -> {
					entry.addAnswer((List<Unifiable<?>>) (List<?>) answerTerm);
					// Don't call k here - just cache and continue the goal
					return done(Nothing.nothing());
				});
			});

			// After caching completes, mark as complete and then consume answers like a slave
			return cachingFiber.flatMap(result -> {
				entry.markComplete();

				// Now consume answers from the cache to continue the query
				return consumeAnswers(entry, k, initialPkg, args, new AtomicInteger(0));
			});
		};
	}

	/**
	 * Slave consumer: reads answer terms from the cache and unifies with current arguments.
	 */
	private static Cont<Package, Nothing> consumerCont(TableEntry entry, Package initialPkg, List<Unifiable> args) {
		return k -> consumeAnswers(entry, k, initialPkg, args, new AtomicInteger(0));
	}

	/**
	 * Recursively consume answer terms from the cache.
	 * Suspends when the cache is exhausted and resumes when new answers arrive.
	 *
	 * For each answer term, we unify it with the current arguments.
	 * If unification succeeds, we yield the unified Package.
	 */
	private static Fiber<Nothing> consumeAnswers(
			TableEntry entry,
			Fiber.Fn<Package, Nothing> k,
			Package initialPkg,
			List<Unifiable> args,
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
					List<Unifiable<?>> answerTerm = ((TableEntry.Answer) status).getAnswerTerm();
					// Unify the answer term with our arguments - MFiber.getOrElse returns Fiber
					return unifyArguments(initialPkg, args, answerTerm)
						.map(unifiedPkg -> {
							// Unification succeeded, yield result and continue
							index.incrementAndGet();
							return k.apply(unifiedPkg).flatMap(__ ->
								Fiber.defer(() -> consumeAnswers(entry, k, initialPkg, args, index))
							);
						})
						.getOrElse(() -> {
							// Unification failed, try next answer
							index.incrementAndGet();
							return Fiber.defer(() -> consumeAnswers(entry, k, initialPkg, args, index));
						})
						.flatMap(fib -> fib);
				}
			} catch (Exception e) {
				throw new RuntimeException("Error getting answer from table", e);
			}
		}

		// Answer not ready - suspend until it arrives
		Awaitable<TableEntry.AnswerStatus> awaitable = () -> answerFuture;

		return awaitable.await((TableEntry.AnswerStatus status) -> {
			if (status instanceof TableEntry.Done) {
				// Master is done, no more answers
				return done(Nothing.nothing());
			} else if (status instanceof TableEntry.Answer) {
				List<Unifiable<?>> answerTerm = ((TableEntry.Answer) status).getAnswerTerm();
				// Unify the answer term with our arguments - MFiber.getOrElse returns Fiber
				return unifyArguments(initialPkg, args, answerTerm)
					.map(unifiedPkg -> {
						// Unification succeeded, yield result and continue
						index.incrementAndGet();
						return k.apply(unifiedPkg).flatMap(__ ->
							Fiber.defer(() -> consumeAnswers(entry, k, initialPkg, args, index))
						);
					})
					.getOrElse(() -> {
						// Unification failed, try next answer
						index.incrementAndGet();
						return Fiber.defer(() -> consumeAnswers(entry, k, initialPkg, args, index));
					})
					.flatMap(fib -> fib);
			} else {
				return done(Nothing.nothing());
			}
		});
	}

	/**
	 * Unify a list of arguments with a list of answer terms.
	 * Returns MFiber with the unified Package, or MFiber.none() if unification fails.
	 */
	@SuppressWarnings("unchecked")
	private static MFiber<Package> unifyArguments(Package pkg, List<Unifiable> args, List<Unifiable<?>> answerTerm) {
		if (args.size() != answerTerm.size()) {
			return MFiber.none();
		}

		MFiber<Package> result = mdone(pkg);
		for (int i = 0; i < args.size(); i++) {
			final int idx = i;
			result = result.flatMap(currentPkg -> {
				Unifiable arg = args.get(idx);
				Unifiable answerArg = (Unifiable) answerTerm.get(idx);
				return MiniKanren.unify(currentPkg, arg, answerArg);
			});
		}
		return result;
	}
}
