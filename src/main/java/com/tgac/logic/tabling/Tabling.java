package com.tgac.logic.tabling;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Awaitable;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
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
						System.out.println("[DEBUG] path - MASTER for key: " + key);
					}
					// We are the master
					return producerCont(entry, goalFactory.apply(args), pkg, args).apply(k);
				} else {
					if (goalName.equals("path")) {
						System.out.println("[DEBUG] path - SLAVE for key: " + key);
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
			System.out.println("[DEBUG] MASTER starting execution for: " + entry.getCall());

			// Execute the goal with a continuation that both caches AND passes answers forward
			Fiber<Nothing> goalFiber = goal.apply(initialPkg).apply(answerPkg -> {
				// For each answer Package, reify the arguments to create answer term
				return reifyArguments(answerPkg, args).flatMap(answerTerm -> {
					// Check if this answer is a duplicate (already cached)
					if (isDuplicate(entry, (List<Unifiable<?>>) (List<?>) answerTerm)) {
						System.out.println("[DEBUG] MASTER skipping duplicate answer: " + answerTerm + " for " + entry.getCall());
						// Duplicate - don't cache or pass to continuation (goal fails for this branch)
						return done(Nothing.nothing());
					}

					System.out.println("[DEBUG] MASTER caching NEW answer: " + answerTerm + " for " + entry.getCall());

					// Cache the answer term (for slaves to consume)
					entry.addAnswer((List<Unifiable<?>>) (List<?>) answerTerm);

					// ALSO pass the answer to the continuation (return as singleton stream)
					// This allows answers to flow through the query
					return k.apply(answerPkg);
				});
			});

			// After goal execution completes, mark as complete
			return goalFiber.flatMap(result -> {
				System.out.println("[DEBUG] MASTER marking complete: " + entry.getCall());
				entry.markComplete();
				return done(Nothing.nothing());
			});
		};
	}

	/**
	 * Check if an answer term is already in the cache.
	 * Uses structural equality to detect duplicates.
	 */
	private static boolean isDuplicate(TableEntry entry, List<Unifiable<?>> answerTerm) {
		for (int i = 0; i < entry.getAnswerCount(); i++) {
			List<Unifiable<?>> cached = entry.getAnswerAt(i);
			if (answersEqual(cached, answerTerm)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if two answer terms are equal.
	 * Answer terms are lists of Unifiables that should be structurally equal.
	 * LVars (from reification) are compared by name, not object identity.
	 */
	@SuppressWarnings("unchecked")
	private static boolean answersEqual(List<Unifiable<?>> a, List<Unifiable<?>> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			Unifiable<?> aElem = a.get(i);
			Unifiable<?> bElem = b.get(i);

			// For LVars, compare by name (reified LVars are fresh objects)
			if (aElem.asVar().isDefined() && bElem.asVar().isDefined()) {
				LVar<Object> aVar = (LVar<Object>) aElem.asVar().get();
				LVar<Object> bVar = (LVar<Object>) bElem.asVar().get();
				if (!aVar.getName().equals(bVar.getName())) {
					return false;
				}
			} else if (!aElem.equals(bElem)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Slave consumer: reads answer terms from the cache and unifies with current arguments.
	 */
	private static Cont<Package, Nothing> consumerCont(TableEntry entry, Package initialPkg, List<Unifiable> args) {
		System.out.println("[DEBUG] SLAVE starting consumption for: " + entry.getCall());
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
		System.out.println("[DEBUG] consumeAnswers waiting for answer at index " + currentIndex + " for " + entry.getCall());

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
