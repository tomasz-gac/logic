package com.tgac.logic.separate;

// ABOUTME: Lattice laws for the Neq store: meet is disequality-record union,
// ABOUTME: so more records = lower — claimed for the coverage gate.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.unification.LVar;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(NeqConstraints.class)
public class NeqConstraintsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(NeqConstraintsLawsTest.class);
	}

	private static final LVar<?> X = (LVar<?>) lvar().asVar().get();
	private static final LVar<?> Y = (LVar<?>) lvar().asVar().get();
	private static final NeqConstraint X_IS_NOT_1 = NeqConstraint.of(HashMap.of(X, lval(1L)));
	private static final NeqConstraint Y_IS_NOT_2 = NeqConstraint.of(HashMap.of(Y, lval(2L)));
	private static final NeqConstraint X_Y_DIFFER = NeqConstraint.of(HashMap.of(X, Y));

	@Test
	public void recordUnionIsAMeetSemilattice() {
		List<NeqConstraints> samples = Arrays.asList(
				NeqConstraints.of(LinkedHashSet.empty()),
				NeqConstraints.of(LinkedHashSet.of(X_IS_NOT_1)),
				NeqConstraints.of(LinkedHashSet.of(Y_IS_NOT_2, X_Y_DIFFER)),
				NeqConstraints.of(LinkedHashSet.of(X_IS_NOT_1, X_Y_DIFFER)));
		SemilatticeLaws.checkMeet(samples);
	}
}
