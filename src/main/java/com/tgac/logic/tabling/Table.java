package com.tgac.logic.tabling;

// ABOUTME: Maps tabled goal calls to their table entries for the duration of one solve.
// ABOUTME: Rides the package's constraint-store map so every derived state shares it.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The table that maps tabled goal calls to their table entries.
 *
 * Each unique call (identified by goal name and reified arguments) gets its
 * own {@link TableEntry} where answers are cached. The table is scoped to a
 * single solve: {@link Goal#solve} seeds a fresh one into the root package's
 * store map, and all packages derived during the search share it.
 *
 * It is a plain {@link Store} — not a constraint store — so constraint
 * processing ignores it; the store map is only its transport.
 */
public class Table implements Store {

	/** Map from calls to their table entries */
	private final ConcurrentHashMap<Call, TableEntry> entries = new ConcurrentHashMap<>();

	private Table() {
	}

	public static Table empty() {
		return new Table();
	}

	/**
	 * Get or create a table entry for the given call.
	 * If this is the first time we've seen this call, a new TableEntry is created.
	 */
	public TableEntry getOrCreateEntry(Call call) {
		return entries.computeIfAbsent(call, TableEntry::new);
	}

	/**
	 * Get an existing table entry, or null if the call hasn't been tabled yet.
	 */
	public Collection<TableEntry> entries() {
		return entries.values();
	}

	/**
	 * A SEALED entry whose call subsumes {@code key}, or null. Linear scan:
	 * entries per solve are few, and the scan runs only on exact misses —
	 * SubsumptionMap is this lookup's planned generalization when its next
	 * customer (the adornment memo) arrives.
	 */
	public TableEntry findSealedSubsumer(Call key) {
		for (TableEntry e : entries.values()) {
			if (e.isComplete() && e.getCall().subsumes(key)) {
				return e;
			}
		}
		return null;
	}

	public TableEntry getEntry(Call call) {
		return entries.get(call);
	}

	/**
	 * Get the number of distinct calls currently in the table.
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * Check if the table contains an entry for the given call.
	 */
	public boolean contains(Call call) {
		return entries.containsKey(call);
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
		return "Table{entries=" + entries.size() + "}";
	}
}
