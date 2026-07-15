package com.tgac.logic.tabling.primitives;

// ABOUTME: Join-semilattice laws for JoinMap — a map into a join-semilattice
// ABOUTME: value; the min-plus value-fold is exercised alongside key dedup.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
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
		JoinMap<Long, Long> m = JoinMap.empty(Semirings.MIN_PLUS);
		for (Tuple2<Long, Long> e : entries) {
			m = m.append(e._1, e._2).getOrElse(m);
		}
		return m;
	}

	@Test
	public void joinMapsFormAJoinSemilattice() {
		SemilatticeLaws.checkJoin(Arrays.asList(
				JoinMap.<Long, Long> empty(Semirings.MIN_PLUS),
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
		JoinMap<Long, Long> m = of(Tuple.of(1L, 5L));

		// worse cost: min leaves the entry unchanged — no strict ascent, no wake
		assertThat(m.append(1L, 8L).isDefined()).isFalse();

		// cheaper cost: the fold moves — ascent, value replaced in place
		Option<JoinMap<Long, Long>> improved = m.append(1L, 3L);
		assertThat(improved.isDefined()).isTrue();
		assertThat(improved.get().get(0)._2).isEqualTo(3L);

		// a fresh key is always an ascent
		assertThat(m.append(2L, 9L).isDefined()).isTrue();
	}
}
