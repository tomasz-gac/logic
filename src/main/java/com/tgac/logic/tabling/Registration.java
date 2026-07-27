package com.tgac.logic.tabling;

// ABOUTME: A consumer parked as data — the subscriber of the answer log,
// ABOUTME: carrying both what it waits for (implicit) and who it works for.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.primitives.Fixpoint;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import lombok.Value;

/**
 * The parked subscriber: its continuation, the state it was consuming in,
 * the arguments it unifies answers against, the cache index it will resume
 * from, and THE FIXPOINT IT WORKS FOR (null at top level). Where it parks
 * says what it WAITS FOR; {@code enclosing} says whose ledger its work is
 * billed to — resolved once from the parked package's {@link EnclosingCall}
 * coat.
 */
@Value
public class Registration {
	Fiber.Fn<Package, Nothing> continuation;
	Package pkg;
	Unifiable<?> argsTerm;
	int nextIndex;

	/** The fixpoint of the call whose execution this reader is a line of - whose
	 * ledger its work bills to - or null at top level. */
	Fixpoint<?, Registration> enclosing;

	/** The reader at the call site: cursor at the start of the cache. */
	static Registration reader(Fiber.Fn<Package, Nothing> continuation, Package pkg, Unifiable<?> argsTerm) {
		TableEntry<?> coat = EnclosingCall.entryOf(pkg);
		return new Registration(continuation, pkg, argsTerm, 0,
				coat == null ? null : coat.getFixpoint());
	}

	/** The same reader, one answer further along. */
	Registration advanced() {
		return new Registration(continuation, pkg, argsTerm, nextIndex + 1, enclosing);
	}
}
