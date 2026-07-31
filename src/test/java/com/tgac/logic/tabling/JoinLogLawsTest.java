package com.tgac.logic.tabling;

// ABOUTME: Join-semilattice laws for JoinLog - the discrete answer fold; the
// ABOUTME: min-plus value-fold is exercised alongside key dedup.

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

@LawsFor(JoinLog.class)
public class JoinLogLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(JoinLogLawsTest.class);
	}

	@SafeVarargs
	private static JoinLog<Long, Long> of(Tuple2<Long, Long>... entries) {
		JoinLog<Long, Long> log = JoinLog.empty(Semirings.MIN_PLUS);
		for (Tuple2<Long, Long> e : entries) {
			log = log.append(e._1, e._2).getOrElse(log);
		}
		return log;
	}

	@Test
	public void joinLogsFormASemilattice() {
		SemilatticeLaws.check(Arrays.asList(
				JoinLog.<Long, Long> empty(Semirings.MIN_PLUS),
				of(Tuple.of(1L, 5L)),
				of(Tuple.of(1L, 9L)),                       // shared key, cheaper wins in the fold
				of(Tuple.of(1L, 5L), Tuple.of(2L, 3L)),
				of(Tuple.of(2L, 3L), Tuple.of(3L, 8L))));
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
		JoinLog<Long, Long> log = of(Tuple.of(1L, 5L));

		// worse cost: min leaves the entry unchanged — no strict ascent, no wake
		assertThat(log.append(1L, 8L).isDefined()).isFalse();
		assertThat(log.append(1L, 3L).isDefined()).isTrue();
	}
}
