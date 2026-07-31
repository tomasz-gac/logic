package com.tgac.logic.tabling;

// ABOUTME: Join-semilattice laws for Antichain - interval keys under containment;
// ABOUTME: union-then-drop-dominated is ACI and an eviction is an ascent.

import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Antichain.class)
public class AntichainLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(AntichainLawsTest.class);
	}

	private static final Antichain.Dominance<Tuple2<Integer, Integer>> CONTAINS =
			(a, b) -> a._1 <= b._1 && b._2 <= a._2;

	@Test
	public void antichainsFormASemilattice() {
		Antichain<Tuple2<Integer, Integer>, Boolean> empty = Antichain.empty(Semirings.BOOLEAN, CONTAINS);
		SemilatticeLaws.check(Arrays.asList(
				empty,
				empty.append(Tuple.of(1, 2), true).get(),
				empty.append(Tuple.of(1, 3), true).get(),
				empty.append(Tuple.of(5, 6), true).get(),
				empty.append(Tuple.of(1, 2), true).get()
						.append(Tuple.of(5, 6), true).get()));
	}
}
