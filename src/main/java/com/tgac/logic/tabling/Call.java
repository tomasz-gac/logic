package com.tgac.logic.tabling;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import io.vavr.collection.List;
import lombok.Value;

import static com.tgac.functional.fibers.Fiber.done;

/**
 * Represents a call to a tabled goal with specific arguments.
 *
 * Two calls are considered equal if they have the same goal name and
 * their arguments are equal after walking through the substitution.
 * This allows us to identify duplicate calls that should share cached answers.
 */
@Value
public class Call {
	String goalName;
	List<Term<?>> arguments;

	/**
	 * Create a Call from a goal name and arguments.
	 */
	public static Call of(String goalName, Term<?>... args) {
		return new Call(goalName, List.of(args));
	}

	/**
	 * Create a Call from a goal name and argument list.
	 */
	public static Call of(String goalName, List<Term<?>> args) {
		return new Call(goalName, args);
	}

	/**
	 * Check if this call contains any LVars at any nesting depth.
	 * Should only be called on a walked Call.
	 * Uses Fiber to avoid stack overflow on deeply nested structures.
	 *
	 * @return Fiber that yields true if any argument contains LVars, false if ground
	 */
	public Fiber<Boolean> containsLVarsFiber() {
		// Check each argument for LVars at any depth using MiniKanren.containsLVars
		Fiber<Boolean> result = done(false);

		for (Term<?> arg : arguments) {
			result = result.flatMap(hasLVars -> {
				if (hasLVars) {
					return done(true);  // Already found LVars, short-circuit
				}
				return MiniKanren.containsLVars(arg);
			});
		}

		return result;
	}

	/**
	 * Walk this call's arguments deeply through the given substitutions.
	 * Uses Fiber to avoid stack overflow on deeply nested structures.
	 * Returns a Fiber yielding a new Call with deeply walked arguments.
	 */
	public Fiber<Call> walkFiber(Package pkg) {
		return walkAllArguments(pkg).map(walkedArgs ->
			new Call(goalName, walkedArgs)
		);
	}

	/**
	 * Helper: deeply walk all arguments using walkAll, returning Fiber of walked argument list.
	 */
	@SuppressWarnings("unchecked")
	private Fiber<List<Term<?>>> walkAllArguments(Package pkg) {
		// Start with empty list wrapped in Fiber
		Fiber<List<Term<?>>> result = done(List.empty());

		// For each argument, walk it deeply and append to result
		for (Term<?> arg : arguments) {
			result = result.flatMap(accList ->
				((Fiber<Term<?>>) (Fiber<?>) MiniKanren.walkAll(pkg, arg))
						.map(accList::append));
		}

		return result;
	}

	@Override
	public String toString() {
		return goalName + "(" + arguments.mkString(", ") + ")";
	}
}
