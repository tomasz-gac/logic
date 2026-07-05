package com.tgac.logic.tabling;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
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
	List<Unifiable> arguments;

	/**
	 * Create a Call from a goal name and arguments.
	 */
	public static Call of(String goalName, Unifiable... args) {
		return new Call(goalName, List.of(args));
	}

	/**
	 * Create a Call from a goal name and argument list.
	 */
	public static Call of(String goalName, List<Unifiable> args) {
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

		for (Unifiable arg : arguments) {
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
	private Fiber<List<Unifiable>> walkAllArguments(Package pkg) {
		// Start with empty list wrapped in Fiber
		Fiber<List<Unifiable>> result = done(List.empty());

		// For each argument, walk it deeply and append to result
		for (Unifiable arg : arguments) {
			result = result.flatMap(accList ->
				((Fiber<Unifiable>) MiniKanren.walkAll(pkg, arg))
						.map(accList::append));
		}

		return result;
	}

	/**
	 * Two calls are equal if they have the same goal name and equivalent arguments.
	 * Note: This uses structural equality on arguments after walking.
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Call call = (Call) o;

		if (!goalName.equals(call.goalName)) return false;
		if (arguments.size() != call.arguments.size()) return false;

		// Compare arguments structurally
		for (int i = 0; i < arguments.size(); i++) {
			if (!argumentsEqual(arguments.get(i), call.arguments.get(i))) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Check if two arguments are equal structurally.
	 * Reified calls can contain LVars at any depth, which compare by name for table lookup.
	 */
	private boolean argumentsEqual(Unifiable a, Unifiable b) {
		return MiniKanren.structuralEquals(a, b);
	}

	@Override
	public int hashCode() {
		int result = goalName.hashCode();

		// Hash by argument structure, consistent with argumentsEqual
		for (Unifiable arg : arguments) {
			result = 31 * result + MiniKanren.structuralHash(arg);
		}

		return result;
	}

	@Override
	public String toString() {
		return goalName + "(" + arguments.mkString(", ") + ")";
	}
}
