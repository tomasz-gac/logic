package com.tgac.logic.tabling;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The global table that maps tabled goal calls to their table entries.
 *
 * This maintains the master/slave coordination for all tabled goals.
 * Each unique call (identified by goal name and ground arguments) gets
 * its own TableEntry where answers are cached.
 */
public class Table {
	/** Global singleton instance */
	private static final Table INSTANCE = new Table();

	/** Map from calls to their table entries */
	private final ConcurrentHashMap<Call, TableEntry> entries = new ConcurrentHashMap<>();

	private Table() {
		// Private constructor for singleton
	}

	/**
	 * Get the global table instance.
	 */
	public static Table instance() {
		return INSTANCE;
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
	public TableEntry getEntry(Call call) {
		return entries.get(call);
	}

	/**
	 * Clear all table entries.
	 * This should be called between queries to prevent answer pollution.
	 */
	public void clear() {
		entries.clear();
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
	public String toString() {
		return "Table{entries=" + entries.size() + "}";
	}
}
