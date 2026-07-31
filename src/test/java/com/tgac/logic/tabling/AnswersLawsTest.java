package com.tgac.logic.tabling;

// ABOUTME: Join-semilattice laws for Answers, the product cell value - checked
// ABOUTME: over the ground slice; the covered component's laws live in AntichainLawsTest.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.unification.Reified;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Answers.class)
public class AnswersLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(AnswersLawsTest.class);
	}

	private static Answers<Object> of(Object... terms) {
		Answers<Object> answers = Answers.empty(presence());
		for (Object term : terms) {
			answers = answers.append(AnswerKey.of((Reified<?>) lval(term)), Boolean.TRUE)
					.getOrElse(answers);
		}
		return answers;
	}

	@SuppressWarnings("unchecked")
	private static IdempotentSemiring<Object> presence() {
		return (IdempotentSemiring<Object>) (IdempotentSemiring<?>) Semirings.BOOLEAN;
	}

	@Test
	public void answersFormASemilattice() {
		// the product of two semilattices, checked over its ground slice -
		// residue-carrying members need a live constraint store, and the
		// covered component's own laws are proven in AntichainLawsTest
		SemilatticeLaws.check(Arrays.asList(
				of(),
				of("a"),
				of("b"),
				of("a", "b"),
				of("b", "c")));
	}
}
