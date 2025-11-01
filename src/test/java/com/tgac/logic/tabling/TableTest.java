package com.tgac.logic.tabling;

import org.junit.Before;
import org.junit.Test;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

public class TableTest {

	@Before
	public void setup() {
		// Clear table before each test
		Table.instance().clear();
	}

	@Test
	public void testGetOrCreateEntry() {
		Table table = Table.instance();

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
		Table table = Table.instance();

		Call call1 = Call.of("ancestor", lval("alice"), lval("bob"));
		Call call2 = Call.of("ancestor", lval("alice"), lval("charlie"));

		TableEntry entry1 = table.getOrCreateEntry(call1);
		TableEntry entry2 = table.getOrCreateEntry(call2);

		assertThat(entry1).isNotSameAs(entry2);
		assertThat(table.size()).isEqualTo(2);
	}

	@Test
	public void testContains() {
		Table table = Table.instance();

		Call call = Call.of("parent", lval("alice"), lval("bob"));

		assertThat(table.contains(call)).isFalse();

		table.getOrCreateEntry(call);

		assertThat(table.contains(call)).isTrue();
	}

	@Test
	public void testClear() {
		Table table = Table.instance();

		Call call1 = Call.of("ancestor", lval("alice"), lval("bob"));
		Call call2 = Call.of("parent", lval("bob"), lval("charlie"));

		table.getOrCreateEntry(call1);
		table.getOrCreateEntry(call2);

		assertThat(table.size()).isEqualTo(2);

		table.clear();

		assertThat(table.size()).isEqualTo(0);
		assertThat(table.contains(call1)).isFalse();
		assertThat(table.contains(call2)).isFalse();
	}

	@Test
	public void testGetEntry() {
		Table table = Table.instance();

		Call call = Call.of("ancestor", lval("alice"), lval("bob"));

		assertThat(table.getEntry(call)).isNull();

		TableEntry created = table.getOrCreateEntry(call);

		assertThat(table.getEntry(call)).isSameAs(created);
	}
}
