package com.tgac.logic.lattice;

// ABOUTME: Partial-order laws for Imposition's atom leq — the structural
// ABOUTME: default: an imposition entails exactly itself.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Imposition.class)
public class ImpositionLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(ImpositionLawsTest.class);
	}

	@Test
	public void structuralEqualityIsAPartialOrder() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		java.util.List<Atom<FlatConstraints>> samples = Arrays.asList(
				new Imposition<>(FlatConstraints.class, x, FlatSet.of(1, 2)),
				new Imposition<>(FlatConstraints.class, x, FlatSet.of(1, 2)),
				new Imposition<>(FlatConstraints.class, x, FlatSet.of(1)),
				new Imposition<>(FlatConstraints.class, y, FlatSet.of(1, 2)));
		PartialOrderLaws.check(samples);
	}
}
