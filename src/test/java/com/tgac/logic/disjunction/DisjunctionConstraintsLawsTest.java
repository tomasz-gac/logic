package com.tgac.logic.disjunction;

// ABOUTME: Lattice laws for the disjunction store: meet is disjunct union, so
// ABOUTME: more disjuncts = lower — claimed for the coverage gate.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.LinkedHashSet;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(DisjunctionConstraints.class)
public class DisjunctionConstraintsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(DisjunctionConstraintsLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Disjunct X_ONE_OR_TWO = Disjunct.of(
			Posting.bind(X, lval(1)), Posting.bind(X, lval(2)));
	private static final Disjunct Y_TWO_OR_THREE = Disjunct.of(
			Posting.bind(Y, lval(2)), Posting.bind(Y, lval(3)));
	private static final Disjunct EITHER_VAR = Disjunct.of(
			Posting.bind(X, lval(1)), Posting.bind(Y, lval(2)));

	@Test
	public void disjunctUnionIsAMeetSemilattice() {
		java.util.List<DisjunctionConstraints> samples = Arrays.asList(
				DisjunctionConstraints.of(LinkedHashSet.empty()),
				DisjunctionConstraints.of(LinkedHashSet.of(X_ONE_OR_TWO)),
				DisjunctionConstraints.of(LinkedHashSet.of(Y_TWO_OR_THREE, EITHER_VAR)),
				DisjunctionConstraints.of(LinkedHashSet.of(X_ONE_OR_TWO, EITHER_VAR)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
