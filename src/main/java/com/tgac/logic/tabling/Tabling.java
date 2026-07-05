package com.tgac.logic.tabling;

// ABOUTME: Tabled evaluation of logic goals: answers are cached per call and shared,
// ABOUTME: which makes left-recursive and mutually recursive predicates terminate.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.MFiber;
import com.tgac.logic.ckanren.StoreSupport;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.TableEntry.Registration;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.functional.fibers.MFiber.mdone;
import static com.tgac.logic.unification.LVal.lval;

/**
 * Provides tabling (memoization) for logic goals to prevent infinite loops
 * and improve performance by caching answers.
 *
 * The first invocation of a call becomes the master and executes the goal
 * with a caching hook threaded through its continuation: every derived
 * answer is reified, deduplicated and cached before it flows on. Later
 * invocations consume the cache. Nothing ever blocks — a consumer that
 * exhausts the cache parks its continuation in the table entry as data and
 * terminates, and whoever derives a new answer respawns the parked consumers
 * as detached fibers. The search reaches its fixpoint when the scheduler
 * runs out of work; parked consumers left at that point are failed branches.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Tabling {

	/**
	 * Create a tabled version of a goal.
	 *
	 * <pre>
	 * 1. Reify the arguments in the current state to create the lookup key
	 * 2. Look up the call in the solve-scoped table
	 * 3. A new call becomes master and executes the goal, caching answer terms
	 * 4. An existing call consumes cached answer terms, parking when it catches up
	 * </pre>
	 *
	 * @param goalName The name of the goal for identification
	 * @param args The arguments to the goal
	 * @param goalFactory A function that creates the actual goal given the arguments
	 * @return A tabled goal that caches answers
	 */
	public static Goal tabled(String goalName, List<Unifiable> args, Function<List<Unifiable>, Goal> goalFactory) {
		return pkg -> k -> reifyArguments(pkg, args).flatMap(reifiedArgs -> {
			Call key = Call.of(goalName, reifiedArgs);

			Table table = StoreSupport.getConstraintStore(pkg, Table.class);
			TableEntry entry = table.getOrCreateEntry(key);
			return entry.tryBecomeMaster() ?
					produce(entry, goalFactory.apply(args), pkg, args, k) :
					consume(entry, k, pkg, args, 0);
		});
	}

	/**
	 * Master: execute the goal with a caching hook in its continuation.
	 * Each new answer is cached, the consumers parked on the entry are
	 * respawned, and the answer flows on through the query. Duplicate
	 * answers fail their branch.
	 */
	private static Fiber<Nothing> produce(
			TableEntry entry,
			Goal goal,
			Package initialPkg,
			List<Unifiable> args,
			Fiber.Fn<Package, Nothing> k) {
		return goal.apply(initialPkg).apply(answerPkg ->
				reifyArguments(answerPkg, args).flatMap(answerTerm ->
						entry.addAnswer(answerTerm)
								.map(parked -> respawn(entry, parked)
										.flatMap(__ -> k.apply(answerPkg)))
								.getOrElse(() -> done(nothing()))));
	}

	/**
	 * Respawn parked consumers as detached fibers so they pick up the answers
	 * cached since they parked. Whoever derives an answer drives its consequences.
	 */
	private static Fiber<Nothing> respawn(TableEntry entry, List<Registration> parked) {
		Fiber<Nothing> result = done(nothing());
		for (Registration r : parked) {
			result = result.flatMap(__ -> Fiber.detach(Fiber.defer(() ->
					consume(entry, r.getContinuation(), r.getPkg(), r.getArgs(), r.getNextIndex()))));
		}
		return result;
	}

	/**
	 * Consumer: unify cached answer terms with the arguments, yielding each
	 * success to the continuation. On catching up with the cache the consumer
	 * parks itself in the entry and terminates — {@link #respawn} continues it
	 * when new answers arrive, and the fixpoint abandons it otherwise.
	 */
	private static Fiber<Nothing> consume(
			TableEntry entry,
			Fiber.Fn<Package, Nothing> k,
			Package initialPkg,
			List<Unifiable> args,
			int index) {

		if (index < entry.getAnswerCount()) {
			List<Term<?>> answerTerm = entry.getAnswerAt(index);
			// Fresh variables per consumption, so separate consumptions of the
			// same answer don't alias each other's free variables
			return instantiate(answerTerm).flatMap(freshTerm ->
					unifyArguments(initialPkg, args, freshTerm)
							.map(unifiedPkg -> k.apply(unifiedPkg)
									.flatMap(__ -> Fiber.defer(() ->
											consume(entry, k, initialPkg, args, index + 1))))
							.getOrElse(() -> Fiber.defer(() ->
									consume(entry, k, initialPkg, args, index + 1)))
							.flatMap(fib -> fib));
		}

		if (entry.register(new Registration(k, initialPkg, args, index))) {
			return done(nothing());
		}

		// An answer arrived while registering — keep consuming
		return Fiber.defer(() -> consume(entry, k, initialPkg, args, index));
	}

	/**
	 * Reify the whole argument vector as one term, so the canonical variable
	 * numbering spans all arguments: path(X, Y) must not collide with path(X, X).
	 */
	@SuppressWarnings("unchecked")
	private static Fiber<List<Term<?>>> reifyArguments(Package pkg, List<Unifiable> args) {
		return MiniKanren.reify(pkg, lval(args))
				.map(reified -> (List<Term<?>>) (List<?>) reified.get());
	}

	/**
	 * Instantiate a cached answer template with fresh variables.
	 */
	@SuppressWarnings("unchecked")
	private static Fiber<List<Unifiable<?>>> instantiate(List<Term<?>> answerTerm) {
		return MiniKanren.instantiate((Reified<List<Term<?>>>) (Reified<?>) lval(answerTerm))
				.map(fresh -> (List<Unifiable<?>>) (List<?>) fresh.get());
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
