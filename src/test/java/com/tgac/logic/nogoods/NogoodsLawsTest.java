package com.tgac.logic.nogoods;

// ABOUTME: Lattice laws for the nogood store: meet is nogood union, so more
// ABOUTME: nogoods = lower — claimed for the coverage gate.

import com.tgac.logic.constraints.Posting;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Nogoods.class)
public class NogoodsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(NogoodsLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Nogood X_APART = Nogood.of(Posting.bind(X, lval(1)));
	private static final Nogood Y_APART = Nogood.of(Posting.bind(Y, lval(2)));
	private static final Nogood NOT_BOTH = Nogood.of(Posting.all(
			Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));

	@Test
	public void nogoodUnionIsAMeetSemilattice() {
		java.util.List<Nogoods> samples = Arrays.asList(
				Nogoods.of(LinkedHashSet.empty()),
				Nogoods.of(LinkedHashSet.of(X_APART)),
				Nogoods.of(LinkedHashSet.of(Y_APART, NOT_BOTH)),
				Nogoods.of(LinkedHashSet.of(X_APART, NOT_BOTH)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
