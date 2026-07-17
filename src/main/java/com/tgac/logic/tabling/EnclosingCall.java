package com.tgac.logic.tabling;

// ABOUTME: The innermost enclosing tabled call — the EVENT whose ledger pays for
// ABOUTME: this work. Goals are text; calls are events; fibers spawn from events.

import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import io.vavr.control.Option;

/**
 * THE COAT: the innermost enclosing tabled CALL — the call whose execution
 * this code is part of, and so whose ledger pays for its work. A goal is
 * text, a call is an event (one goal object executed under two bindings is
 * two calls, two ledgers, two quiescences); every state is inside many
 * goals but inside exactly one innermost tabled call, or none. It is a
 * BIRTH CERTIFICATE, written once: stamped on the body package when the
 * anonymous master spawns, and carried untouched ever after — an answer
 * ends at the cell wearing it, each reader runs under its own caller's
 * coat, forks inherit it, parked registrations freeze it, wakes resume it.
 * It answers the question nothing else can at a park or respawn deep in a
 * derivation — "which entry's body am I a line of?" — because the package
 * is the only thing that travels through opaque goals. Completion billing
 * reads it: a respawned reader produces ITS OWNER's answers, so it is
 * billed to the call it executes, never to the entry that happened to wake
 * it (docs/design/table-completion.md §4).
 */
final class EnclosingCall implements Packaged {

	/** Top level: the query's code is inside no tabled call — unbilled, ungated. */
	static final EnclosingCall NONE = new EnclosingCall(null);

	private final TableEntry<Object> entry;

	@SuppressWarnings("unchecked")
	EnclosingCall(TableEntry<?> entry) {
		this.entry = (TableEntry<Object>) entry;
	}

	TableEntry<Object> entry() {
		return entry;
	}

	/** The entry of the call {@code pkg} is executing, or null at top level. */
	static TableEntry<Object> entryOf(Package pkg) {
		Option<Packaged> store = pkg.getStores().get(EnclosingCall.class);
		return store.isDefined() ? ((EnclosingCall) store.get()).entry : null;
	}

	static EnclosingCall current(Package pkg) {
		Option<Packaged> store = pkg.getStores().get(EnclosingCall.class);
		return store.isDefined() ? (EnclosingCall) store.get() : NONE;
	}

	@Override
	public String toString() {
		return entry == null ? "inCall(none)" : "inCall(" + entry.getCall() + ")";
	}
}
