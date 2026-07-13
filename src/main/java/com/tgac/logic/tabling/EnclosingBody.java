package com.tgac.logic.tabling;

// ABOUTME: Which goal body the executing code is a line of — dynamic scope over
// ABOUTME: definitions, riding the Package; branch-local by construction.

import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.Package;
import io.vavr.control.Option;

/**
 * THE COAT: which tabled call's body the currently-executing code is a line
 * of. State follows the data; the coat follows the CODE — it changes exactly
 * where control crosses a body boundary: stamped on entry ({@code produce}
 * runs the body under its entry's coat), restored on exit (an answer's
 * downstream is the caller's code, so it leaves wearing the caller's coat),
 * and carried untouched everywhere in between — forks inherit it, parked
 * registrations freeze it, wakes resume it. Completion billing reads it:
 * work is billed to the body it is a line of, never to the entry that
 * happened to wake it (docs/design/table-completion.md §4).
 */
final class EnclosingBody implements Store {

	/** Top level: the query's own code is a line of no body — unbilled, ungated. */
	static final EnclosingBody NONE = new EnclosingBody(null);

	private final TableEntry entry;

	EnclosingBody(TableEntry entry) {
		this.entry = entry;
	}

	TableEntry entry() {
		return entry;
	}

	/** The entry whose body {@code pkg} is executing, or null at top level. */
	static TableEntry entryOf(Package pkg) {
		Option<Store> store = pkg.getStores().get(EnclosingBody.class);
		return store.isDefined() ? ((EnclosingBody) store.get()).entry : null;
	}

	static EnclosingBody current(Package pkg) {
		Option<Store> store = pkg.getStores().get(EnclosingBody.class);
		return store.isDefined() ? (EnclosingBody) store.get() : NONE;
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
		return entry == null ? "inBody(none)" : "inBody(" + entry.getCall() + ")";
	}
}
