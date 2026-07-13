package com.tgac.logic.tabling;

// ABOUTME: Join-semilattice laws for the answer set — the engine's first native
// ABOUTME: citizen of the GROWING half; idempotence IS the dedup discipline.

import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.unification.Reified;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(AnswerSet.class)
public class AnswerSetLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(AnswerSetLawsTest.class);
	}

	private static Reified<?> answer(Object value) {
		return (Reified<?>) lval(value);
	}

	private static AnswerSet of(Object... values) {
		AnswerSet s = AnswerSet.empty();
		for (Object v : values) {
			s = s.append(answer(v)).getOrElse(s);
		}
		return s;
	}

	@Test
	public void answerSetsFormAJoinSemilattice() {
		SemilatticeLaws.checkJoin(Arrays.asList(
				AnswerSet.empty(),
				of(1),
				of(1, 2),
				of(2, 3),
				of(3)));
	}

	@Test
	public void equalityIsKnowledgeNotOrder() {
		// same answers, different arrival order: the same knowledge
		assertThat(of(1, 2)).isEqualTo(of(2, 1));
		// but indexed reads preserve each side's own arrival order
		assertThat(of(2, 1).answerAt(0)).isEqualTo(answer(2));
	}

	@Test
	public void appendRefusesKnownAnswers() {
		// the operational face of join-idempotence: no strict growth, no wake
		AnswerSet s = of(1, 2);
		assertThat(s.append(answer(1)).isDefined()).isFalse();
		assertThat(s.append(answer(3)).isDefined()).isTrue();
	}
}
