package com.tgac.logic.tabling;

// ABOUTME: A consumer parked as data — the subscriber of the answer log,
// ABOUTME: carrying both what it waits for (implicit) and who it works for.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import lombok.Value;

/**
 * The parked subscriber: its continuation, the state it was consuming in,
 * the arguments it unifies answers against, the cache index it will resume
 * from, and THE ENTRY WHOSE BODY IT IS A LINE OF (null at top level).
 * Where it parks says what it WAITS FOR; {@code enclosingBody} says whose
 * definition it belongs to — and so whose ledger its work is billed to —
 * resolved once from the parked package's {@link EnclosingBody} coat.
 */
@Value
public class Registration {
	Fiber.Fn<Package, Nothing> continuation;
	Package pkg;
	Unifiable<?> argsTerm;
	int nextIndex;
	TableEntry enclosingBody;
}
