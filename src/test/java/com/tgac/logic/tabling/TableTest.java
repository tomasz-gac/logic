package com.tgac.logic.tabling;

import org.junit.Test;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

public class TableTest {

	@Test
	public void testGetOrCreateEntry() {
		Table table = Table.empty();

		Call call = Call.of("ancestor", lval("alice"), lval("bob"));

		// First call creates new entry
		TableEntry entry1 = table.getOrCreateEntry(call);
		assertThat(entry1).isNotNull();
		assertThat(entry1.getCall()).isEqualTo(call);

		// Second call with same arguments returns same entry
		TableEntry entry2 = table.getOrCreateEntry(call);
		assertThat(entry2).isSameAs(entry1);

		assertThat(table.size()).isEqualTo(1);
	}

	@Test
	public void testDifferentCallsDifferentEntries() {
		Table table = Table.empty();

		Call call1 = Call.of("ancestor", lval("alice"), lval("bob"));
		Call call2 = Call.of("ancestor", lval("alice"), lval("charlie"));

		TableEntry entry1 = table.getOrCreateEntry(call1);
		TableEntry entry2 = table.getOrCreateEntry(call2);

		assertThat(entry1).isNotSameAs(entry2);
		assertThat(table.size()).isEqualTo(2);
	}

	@Test
	public void testVariantCallsShareEntry() {
		Table table = Table.empty();

		// Reified keys carry canonical variable names, so calls that differ
		// only in variable objects are the same call
		Call call1 = Call.of("ancestor", lval("alice"), lvar("_.0"));
		Call call2 = Call.of("ancestor", lval("alice"), lvar("_.0"));

		TableEntry entry1 = table.getOrCreateEntry(call1);
		TableEntry entry2 = table.getOrCreateEntry(call2);

		assertThat(entry1).isSameAs(entry2);
		assertThat(table.size()).isEqualTo(1);
	}

	@Test
	public void testContains() {
		Table table = Table.empty();

		Call call = Call.of("parent", lval("alice"), lval("bob"));

		assertThat(table.contains(call)).isFalse();

		table.getOrCreateEntry(call);

		assertThat(table.contains(call)).isTrue();
	}

	@Test
	public void testTablesAreIndependent() {
		// Each solve gets its own table; entries must not leak between them
		Table first = Table.empty();
		Table second = Table.empty();

		Call call = Call.of("ancestor", lval("alice"), lval("bob"));
		first.getOrCreateEntry(call);

		assertThat(second.contains(call)).isFalse();
		assertThat(second.size()).isEqualTo(0);
	}

	@Test
	public void testGetEntry() {
		Table table = Table.empty();

		Call call = Call.of("ancestor", lval("alice"), lval("bob"));

		assertThat(table.getEntry(call)).isNull();

		TableEntry created = table.getOrCreateEntry(call);

		assertThat(table.getEntry(call)).isSameAs(created);
	}
}
