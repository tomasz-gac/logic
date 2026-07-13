package com.tgac.logic.tabling.primitives;

// ABOUTME: Join-semilattice laws for JoinSet — the engine's first native citizen
// ABOUTME: of the GROWING half; join-idempotence IS tabling's dedup discipline.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(JoinSet.class)
public class JoinSetLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(JoinSetLawsTest.class);
	}

	private static JoinSet<Long> of(long... values) {
		JoinSet<Long> s = JoinSet.empty();
		for (long v : values) {
			s = s.append(v).getOrElse(s);
		}
		return s;
	}

	@Test
	public void joinSetsFormAJoinSemilattice() {
		SemilatticeLaws.checkJoin(Arrays.asList(
				JoinSet.<Long> empty(),
				of(1),
				of(1, 2),
				of(2, 3),
				of(3)));
	}

	@Test
	public void equalityIsKnowledgeNotOrder() {
		// same elements, different arrival order: the same knowledge
		assertThat(of(1, 2)).isEqualTo(of(2, 1));
		// but indexed reads preserve each side's own arrival order
		assertThat(of(2, 1).get(0)).isEqualTo(2L);
	}

	@Test
	public void appendRefusesKnownElements() {
		// the operational face of join-idempotence: no strict growth, no wake
		JoinSet<Long> s = of(1, 2);
		assertThat(s.append(1L).isDefined()).isFalse();
		assertThat(s.append(3L).isDefined()).isTrue();
	}
}
