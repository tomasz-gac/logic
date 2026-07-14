package com.tgac.logic.weight;

// ABOUTME: The product Semiring<SemiringStore> is lawful — componentwise ⊕/⊗
// ABOUTME: over its participating rings — so weighing many things in one pass is sound.

import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemiringLaws;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(SemiringStore.class)
public class SemiringStoreLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(SemiringStoreLawsTest.class);
	}

	private static final Semiring<SemiringStore> PRODUCT =
			SemiringStore.product(Semirings.COUNTING, Semirings.MIN_PLUS);

	private static SemiringStore store(long count, long cost) {
		return PRODUCT.one()
				.with(Semirings.COUNTING, count)
				.with(Semirings.MIN_PLUS, cost);
	}

	@Test
	public void productIsASemiringComponentwise() {
		List<SemiringStore> xs = Arrays.asList(
				PRODUCT.zero(),
				PRODUCT.one(),
				store(2L, 5L),
				store(3L, 0L),
				store(0L, 12L));
		SemiringLaws.check(PRODUCT, xs);
	}
}
