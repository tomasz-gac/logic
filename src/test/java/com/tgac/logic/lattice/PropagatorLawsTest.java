package com.tgac.logic.lattice;

// ABOUTME: Partial-order laws for Propagator's atom leq — the structural
// ABOUTME: default over (store, name, watched) identity, body excluded.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Propagator.class)
public class PropagatorLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(PropagatorLawsTest.class);
	}

	@Test
	public void nameOverTermsIdentityIsAPartialOrder() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		java.util.List<Atom<FlatConstraints>> samples = Arrays.asList(
				propagator("even", x),
				propagator("even", x),
				propagator("even", y),
				propagator("odd", x));
		PartialOrderLaws.check(samples);
	}

	private static Propagator<FlatConstraints> propagator(String name, Unifiable<Integer> term) {
		return Propagator.of(FlatConstraints.empty(), name,
				Collections.singletonList(term),
				(watched, state) -> Verdict.keep());
	}
}
