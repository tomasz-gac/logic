package com.tgac.logic.tabling;

// ABOUTME: The production a state belongs to, riding the Package as a transport
// ABOUTME: store — branch-local by construction, carried into parked registrations.

import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.Package;
import io.vavr.control.Option;

/**
 * Completion detection's region tag (docs/design/table-completion.md §4):
 * {@code produce} stamps the package before running the body, so any tabled
 * call inside knows whose production it serves, and a {@link
 * TableEntry.Registration} carries the tag for free through its parked
 * package. The answer hook restores the caller's tag before detaching the
 * downstream, so consumed answers count for the consumer's own region.
 */
final class Producer implements Store {

	static final Producer NONE = new Producer(null);

	private final TableEntry entry;

	Producer(TableEntry entry) {
		this.entry = entry;
	}

	TableEntry entry() {
		return entry;
	}

	/** The production {@code pkg} currently serves, or null at top level. */
	static TableEntry of(Package pkg) {
		Option<Store> store = pkg.getStores().get(Producer.class);
		return store.isDefined() ? ((Producer) store.get()).entry : null;
	}

	static Producer current(Package pkg) {
		Option<Store> store = pkg.getStores().get(Producer.class);
		return store.isDefined() ? (Producer) store.get() : NONE;
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
		return entry == null ? "producer(none)" : "producer(" + entry.getCall() + ")";
	}
}
