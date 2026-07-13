package com.tgac.logic.tabling;

// ABOUTME: The innermost enclosing tabled call — the EVENT whose ledger pays for
// ABOUTME: this work. Goals are text; calls are events; fibers spawn from events.

import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.Package;
import io.vavr.control.Option;

/**
 * THE COAT: the innermost enclosing tabled CALL — the call whose execution
 * this code is part of, and so whose ledger pays for its work. A goal is
 * text, a call is an event (one goal object executed under two bindings is
 * two calls, two ledgers, two quiescences); every state is inside many
 * goals but inside exactly one innermost tabled call, or none. State
 * follows the data; the coat follows the CODE — it changes exactly where
 * control crosses a call boundary: stamped on entry ({@code produce} runs
 * the body under its entry's coat), restored on exit (an answer's
 * downstream is the caller's code, so it leaves wearing the caller's coat),
 * and carried untouched everywhere in between — forks inherit it, parked
 * registrations freeze it, wakes resume it. Completion billing reads it:
 * work is billed to the call it executes, never to the entry that happened
 * to wake it (docs/design/table-completion.md §4).
 */
final class EnclosingCall implements Store {

	/** Top level: the query's code is inside no tabled call — unbilled, ungated. */
	static final EnclosingCall NONE = new EnclosingCall(null);

	private final TableEntry entry;

	EnclosingCall(TableEntry entry) {
		this.entry = entry;
	}

	TableEntry entry() {
		return entry;
	}

	/** The entry of the call {@code pkg} is executing, or null at top level. */
	static TableEntry entryOf(Package pkg) {
		Option<Store> store = pkg.getStores().get(EnclosingCall.class);
		return store.isDefined() ? ((EnclosingCall) store.get()).entry : null;
	}

	static EnclosingCall current(Package pkg) {
		Option<Store> store = pkg.getStores().get(EnclosingCall.class);
		return store.isDefined() ? (EnclosingCall) store.get() : NONE;
	}

	@Override
	public Store remove(Stored c) {
		return this;
	}

	@Override
	public Store prepend(Stored c) {
		return this;
	}

	@Override
	public boolean contains(Stored c) {
		return false;
	}

	@Override
	public String toString() {
		return entry == null ? "inCall(none)" : "inCall(" + entry.getCall() + ")";
	}
}
