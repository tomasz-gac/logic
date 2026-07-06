package com.tgac.logic.tabling;

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.ReifiedVar;
import io.vavr.Tuple;
import org.junit.Test;

public class CallTest {

	private static <T> Tabled<T> relation(String name) {
		return Tabling.define(name, args -> Goal.success());
	}

	@Test
	public void testEqualityByRelationAndArguments() {
		Tabled<Object> rel = relation("ancestor");

		Call call1 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "bob")));
		Call call2 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "bob")));

		assertThat(call1).isEqualTo(call2);
		assertThat(call1.hashCode()).isEqualTo(call2.hashCode());
	}

	@Test
	public void testDifferentArgumentsDiffer() {
		Tabled<Object> rel = relation("ancestor");

		Call call1 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "bob")));
		Call call2 = Call.of(rel, (Reified<?>) lval(Tuple.of("alice", "charlie")));

		assertThat(call1).isNotEqualTo(call2);
	}

	@Test
	public void testSameNameDifferentRelationsDiffer() {
		// the cache is keyed on relation identity — a shared display name
		// must not make two relations share answers
		Call call1 = Call.of(relation("path"), (Reified<?>) lval(Tuple.of(1, 2)));
		Call call2 = Call.of(relation("path"), (Reified<?>) lval(Tuple.of(1, 2)));

		assertThat(call1).isNotEqualTo(call2);
	}

	@Test
	public void testAlphaEquivalentArgumentsAreTheSameCall() {
		Tabled<Object> rel = relation("path");

		// reified holes are equal by canonical name
		Call call1 = Call.of(rel, (Reified<?>) lval(Tuple.of(lval(1), ReifiedVar.of("_.0"))));
		Call call2 = Call.of(rel, (Reified<?>) lval(Tuple.of(lval(1), ReifiedVar.of("_.0"))));

		assertThat(call1).isEqualTo(call2);
	}

	@Test
	public void testToStringShowsNameAndArguments() {
		Call call = Call.of(relation("path"), (Reified<?>) lval(Tuple.of(1, 4)));

		assertThat(call.toString()).contains("path").contains("1").contains("4");
	}
}
