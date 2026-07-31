package com.tgac.logic.tabling;

// ABOUTME: Lattice laws for Residues - the ⊗-monoid of the constraint ring: meet
// ABOUTME: is ACI with leq reversing accumulation, the store convention lifted.

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Residues.class)
public class ResiduesLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(ResiduesLawsTest.class);
	}

	private static List<Residues> samples() {
		return Arrays.asList(
				Residues.TRUE,
				Span.factor(0L, 10L),
				Span.factor(3L, 6L),
				Span.factor(20L, 30L),
				Span.factor(0L, 10L).meet(Span.factor(5L, 15L)));
	}

	@Test
	public void residuesFormAMeetSemilatticeWithContainmentOrder() {
		SemilatticeLaws.checkLeqReversesAccumulation(samples());
	}
}
