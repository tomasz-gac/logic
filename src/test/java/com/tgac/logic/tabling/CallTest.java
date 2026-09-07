package com.tgac.logic.tabling;

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Any;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import java.util.Arrays;
import org.junit.Test;

public class CallTest {

	private static <T> Tabled<T> relation() {
		return Tabling.define(args -> Goal.success());
	}

	@Test
	public void testEqualityByRelationAndArguments() {
		Tabled<Object> rel = relation();

		Call call1 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "bob")));
		Call call2 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "bob")));

		assertThat(call1).isEqualTo(call2);
		assertThat(call1.hashCode()).isEqualTo(call2.hashCode());
	}

	@Test
	public void testDifferentArgumentsDiffer() {
		Tabled<Object> rel = relation();

		Call call1 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "bob")));
		Call call2 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "charlie")));

		assertThat(call1).isNotEqualTo(call2);
	}

	@Test
	public void testDistinctRelationsDiffer() {
		// the cache is keyed on relation identity — distinct relations
		// must not share answers even with identical arguments
		Call call1 = Call.of(relation(), (Reified<?>) lval(Tuple.of(1, 2)));
		Call call2 = Call.of(relation(), (Reified<?>) lval(Tuple.of(1, 2)));

		assertThat(call1).isNotEqualTo(call2);
	}

	@Test
	public void testAlphaEquivalentArgumentsAreTheSameCall() {
		Tabled<Object> rel = relation();

		// reified anys are equal by canonical name
		Call call1 = Call.of(rel, (Reified<?>) lval(Tuple.of(lval(1), Any.of(0))));
		Call call2 = Call.of(rel, (Reified<?>) lval(Tuple.of(lval(1), Any.of(0))));

		assertThat(call1).isEqualTo(call2);
	}

	@Test
	public void testValueEqualTokensNameOneRelation() {
		// tokens key by VALUE: two mints of an equal token are the same
		// relation, for exact-key lookup and for subsumption alike — a
		// value-keyed caller (pldb's RelationN) must hit the coverage it
		// recorded under an earlier mint
		Call<Object> wide = Call.of(Arrays.asList("person"),
				(Reified<?>) lval(Tuple.of(lval(1), Any.of(0))));
		Call<Object> narrow = Call.of(Arrays.asList("person"),
				(Reified<?>) lval(Tuple.of(lval(1), lval(2))));

		assertThat(wide.getRelation()).isNotSameAs(narrow.getRelation());
		assertThat(wide.subsumes(narrow)).isTrue();
	}

	@Test
	public void testToStringShowsArguments() {
		Call<?> call = Call.of(relation(), (Reified<?>) lval(Tuple.of(1, 4)));

		assertThat(call.toString()).contains("1").contains("4");
	}

	@Test
	public void testAnyIdentityTokenKeysACall() {
		// the relation slot is generic: any identity token keys the cache,
		// and the whole subsumption stack answers for it — token identity,
		// argument subsumption, residue entailment
		Object token = new Object();
		Call<Object> wide = Call.of(token, (Reified<?>) lval(Tuple.of(lval(1), Any.of(0))));
		Call<Object> narrow = Call.of(token, (Reified<?>) lval(Tuple.of(lval(1), lval(2))));

		assertThat(wide.subsumes(narrow)).isTrue();
		assertThat(narrow.subsumes(wide)).isFalse();

		Table table = Table.empty();
		TableEntry<Object> entry = table.getOrCreateEntry(wide);
		assertThat(table.reusableSubsumer(narrow)).isSameAs(entry);
		assertThat(table.reusableSubsumer(
				Call.of(new Object(), narrow.getArguments())))
				.describedAs("a different token never shares entries")
				.isNull();
	}
}
