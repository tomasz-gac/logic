package com.tgac.logic.tabling;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.ReifiedVar;
import io.vavr.Tuple;
import org.junit.Test;

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

public class TableTest {

	private static <T> Tabled<T> relation() {
		return Tabling.define(args -> Goal.success());
	}

	private static Call call(Tabled<?> relation, Object args) {
		return Call.of(relation, (Reified<?>) lval(args));
	}

	@Test
	public void testGetOrCreateEntry() {
		Table table = Table.empty();
		Tabled<Object> ancestor = relation();

		Call call = call(ancestor, Tuple.of("alice", "bob"));

		// First call creates new entry
		TableEntry entry1 = table.getOrCreateEntry(call);
		assertThat(entry1).isNotNull();
		assertThat(entry1.getCall()).isEqualTo(call);

		// Second call with same arguments returns same entry
		TableEntry entry2 = table.getOrCreateEntry(call(ancestor, Tuple.of("alice", "bob")));
		assertThat(entry2).isSameAs(entry1);

		assertThat(table.size()).isEqualTo(1);
	}

	@Test
	public void testDifferentCallsDifferentEntries() {
		Table table = Table.empty();
		Tabled<Object> ancestor = relation();

		TableEntry entry1 = table.getOrCreateEntry(call(ancestor, Tuple.of("alice", "bob")));
		TableEntry entry2 = table.getOrCreateEntry(call(ancestor, Tuple.of("alice", "charlie")));

		assertThat(entry1).isNotSameAs(entry2);
		assertThat(table.size()).isEqualTo(2);
	}

	@Test
	public void testVariantCallsShareEntry() {
		Table table = Table.empty();
		Tabled<Object> ancestor = relation();

		// Reified keys carry canonical hole names, so variant calls are the same call
		TableEntry entry1 = table.getOrCreateEntry(
				call(ancestor, Tuple.of(lval("alice"), ReifiedVar.of("_.0"))));
		TableEntry entry2 = table.getOrCreateEntry(
				call(ancestor, Tuple.of(lval("alice"), ReifiedVar.of("_.0"))));

		assertThat(entry1).isSameAs(entry2);
		assertThat(table.size()).isEqualTo(1);
	}

	@Test
	public void testDistinctRelationsDoNotShareEntries() {
		Table table = Table.empty();

		TableEntry entry1 = table.getOrCreateEntry(call(relation(), Tuple.of(1, 2)));
		TableEntry entry2 = table.getOrCreateEntry(call(relation(), Tuple.of(1, 2)));

		assertThat(entry1).isNotSameAs(entry2);
		assertThat(table.size()).isEqualTo(2);
	}

	@Test
	public void testContains() {
		Table table = Table.empty();
		Call call = call(relation(), Tuple.of("alice", "bob"));

		assertThat(table.contains(call)).isFalse();

		table.getOrCreateEntry(call);

		assertThat(table.contains(call)).isTrue();
	}

	@Test
	public void testTablesAreIndependent() {
		// Each solve gets its own table; entries must not leak between them
		Table first = Table.empty();
		Table second = Table.empty();

		Call call = call(relation(), Tuple.of("alice", "bob"));
		first.getOrCreateEntry(call);

		assertThat(second.contains(call)).isFalse();
		assertThat(second.size()).isEqualTo(0);
	}

	@Test
	public void testGetEntry() {
		Table table = Table.empty();
		Call call = call(relation(), Tuple.of("alice", "bob"));

		assertThat(table.getEntry(call)).isNull();

		TableEntry created = table.getOrCreateEntry(call);

		assertThat(table.getEntry(call)).isSameAs(created);
	}
}
