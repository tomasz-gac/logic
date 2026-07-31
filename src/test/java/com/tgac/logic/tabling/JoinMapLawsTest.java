package com.tgac.logic.tabling;

// ABOUTME: Join-semilattice laws for JoinMap - the one answer carrier; the min-plus
// ABOUTME: value fold and the Condition fold are both exercised alongside key dedup.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(JoinMap.class)
public class JoinMapLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(JoinMapLawsTest.class);
	}

	@SafeVarargs
	private static JoinMap<Long, Long> of(Tuple2<Long, Long>... entries) {
		JoinMap<Long, Long> map = JoinMap.empty(Semirings.MIN_PLUS);
		for (Tuple2<Long, Long> e : entries) {
			map = map.append(e._1, e._2).getOrElse(map);
		}
		return map;
	}

	@Test
	public void joinMapsFormASemilattice() {
		SemilatticeLaws.check(Arrays.asList(
				JoinMap.<Long, Long> empty(Semirings.MIN_PLUS),
				of(Tuple.of(1L, 5L)),
				of(Tuple.of(1L, 9L)),                       // shared key, cheaper wins in the fold
				of(Tuple.of(1L, 5L), Tuple.of(2L, 3L)),
				of(Tuple.of(2L, 3L), Tuple.of(3L, 8L))));
	}

	@Test
	public void conditionValuedMapsFormASemilattice() {
		JoinMap<String, Condition> empty = JoinMap.empty(Condition.RING);
		SemilatticeLaws.check(Arrays.asList(
				empty,
				empty.append("t", Condition.of(Span.factor(3L, 6L))).get(),
				empty.append("t", Condition.of(Span.factor(0L, 10L))).get(),
				empty.append("t", Condition.ONE).get(),
				empty.append("t", Condition.of(Span.factor(3L, 6L))).get()
						.append("u", Condition.of(Span.factor(20L, 30L))).get()));
	}

	@Test
	public void equalityIsKnowledgeNotOrder() {
		// same key→value bindings, different arrival order: the same knowledge
		assertThat(of(Tuple.of(1L, 5L), Tuple.of(2L, 3L)))
				.isEqualTo(of(Tuple.of(2L, 3L), Tuple.of(1L, 5L)));
		// but indexed reads preserve each side's own arrival order
		assertThat(of(Tuple.of(2L, 3L), Tuple.of(1L, 5L)).get(0)._1).isEqualTo(2L);
	}

	@Test
	public void appendAscendsOnlyWhenTheFoldGrows() {
		JoinMap<Long, Long> map = of(Tuple.of(1L, 5L));

		// worse cost: min leaves the entry unchanged — no strict ascent, no wake
		assertThat(map.append(1L, 8L).isDefined()).isFalse();
		assertThat(map.append(1L, 3L).isDefined()).isTrue();
	}
}
