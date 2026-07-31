package com.tgac.logic.tabling;

// ABOUTME: Laws for the constraint semiring: Condition is a semilattice under ⊕,
// ABOUTME: and its ring is bounded — 1 ⊕ a = 1 is absorption, so a* = 1.


import com.tgac.functional.algebra.laws.BoundedSemiringLaws;
import com.tgac.functional.algebra.laws.IdempotentSemiringLaws;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.functional.algebra.laws.SemiringLaws;
import com.tgac.functional.algebra.laws.StarLaws;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Condition.class)
public class ConditionLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(ConditionLawsTest.class);
	}

	private static List<Condition> samples() {
		return Arrays.asList(
				Condition.ZERO,
				Condition.ONE,
				Condition.of(Span.factor(0L, 10L)),
				Condition.of(Span.factor(3L, 6L)),
				Condition.of(Span.factor(20L, 30L)),
				Condition.of(Span.factor(3L, 6L)).or(Condition.of(Span.factor(20L, 30L))));
	}

	@Test
	public void conditionsFormASemilattice() {
		SemilatticeLaws.check(samples());
	}

	@Test
	public void theConstraintRingIsABoundedSemiring() {
		List<Condition> xs = samples();
		SemiringLaws.check(Condition.RING, xs);
		IdempotentSemiringLaws.check(Condition.RING, xs);
		StarLaws.check(Condition.RING, xs);
		BoundedSemiringLaws.check(Condition.RING, xs);
	}
}
