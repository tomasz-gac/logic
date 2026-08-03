package com.tgac.logic.tabling;

// ABOUTME: One consumer's reading state: continuation, call-site package, args
// ABOUTME: and log cursor - carried by the live consuming frame, never stored.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Unifiable;
import lombok.Value;

/**
 * A reader: the consumer's continuation, the state it consumes in, the
 * arguments it unifies answers against, and the ascent-log index it reads
 * next — ONE cursor, because the cell's log is the single enumeration of
 * everything that can happen to an entry's answers. A parameter bundle for
 * the LIVE consuming frame — when the reader catches up, the frame itself
 * parks at the entry's channel (Fiber.await) and this object simply rides
 * its captured state; nothing stores it. The channel it parks at says what
 * it waits for; the frame's ambient scope says whose ledger pays for its
 * work.
 */
@Value
public class Reader {
	Fiber.Fn<Package, Nothing> continuation;
	Package pkg;
	Unifiable<?> argsTerm;
	int cursor;

	/**
	 * The solve's table, reached through the caller's package — the shared
	 * transport store every branch of one solve names identically.
	 */
	public Table getTable() {
		return pkg.getStore(Table.class);
	}

	/**
	 * An INSIDE reader consumes from within some tabled call's body — its
	 * call site was reached while a master was executing, so its package
	 * carries the body stamp. Inside readers stream every ascent as fixpoint
	 * fuel; outside readers receive only final values and wait for the seal.
	 */
	public boolean isInside() {
		return InBody.on(pkg);
	}

	/** The reader at the call site: cursor at the start of the log. */
	static Reader of(Fiber.Fn<Package, Nothing> continuation, Package pkg, Unifiable<?> argsTerm) {
		return new Reader(continuation, pkg, argsTerm, 0);
	}

	/** The same reader, {@code ascents} further along the log. */
	Reader advanced(int ascents) {
		return new Reader(continuation, pkg, argsTerm, cursor + ascents);
	}
}
