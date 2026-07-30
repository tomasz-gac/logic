package com.tgac.logic.tabling;

// ABOUTME: One consumer's reading state: continuation, call-site package, args
// ABOUTME: and cursor - carried by the live consuming frame, never stored.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import lombok.Value;

/**
 * A reader: the consumer's continuation, the state it consumes in, the
 * arguments it unifies answers against, and the cache index it reads next.
 * A parameter bundle for the LIVE consuming frame — when the reader catches
 * up, the frame itself parks at the entry's channel (Fiber.await) and this
 * object simply rides its captured state; nothing stores it. The channel it
 * parks at says what it WAITS FOR; the coat says whose call's execution it
 * belongs to.
 */
@Value
public class Reader {
	Fiber.Fn<Package, Nothing> continuation;
	Package pkg;
	Unifiable<?> argsTerm;
	int nextIndex;

	/**
	 * Whether this reader runs inside some tabled call's body (the
	 * EnclosingCall coat was present at the call site). A coated reader's
	 * contribution rides its captured edges; only top-level readers replay.
	 */
	boolean coated;

	/**
	 * The solve's table, reached through the caller's package — the shared
	 * transport store every branch of one solve names identically.
	 */
	public Table getTable() {
		return pkg.getStore(Table.class);
	}

	/** The reader at the call site: cursor at the start of the cache. */
	static Reader of(Fiber.Fn<Package, Nothing> continuation, Package pkg, Unifiable<?> argsTerm) {
		return new Reader(continuation, pkg, argsTerm, 0,
				EnclosingCall.entryOf(pkg) != null);
	}

	/** The same reader, one answer further along. */
	Reader advanced() {
		return new Reader(continuation, pkg, argsTerm, nextIndex + 1, coated);
	}
}
