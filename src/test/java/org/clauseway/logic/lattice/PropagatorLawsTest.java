package org.clauseway.logic.lattice;

// ABOUTME: Partial-order laws for Propagator's atom leq — the structural
// ABOUTME: default over (store, name, watched) identity, body excluded.

import static org.clauseway.logic.unification.terms.LVar.lvar;

import org.clauseway.functional.laws.LawChecker;
import org.clauseway.functional.laws.LawsFor;
import org.clauseway.functional.algebra.laws.PartialOrderLaws;
import org.clauseway.logic.constraints.store.Atom;
import org.clauseway.logic.lattice.LatticeFactorTest.FlatConstraints;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.AfterClass;
import org.junit.Test;
import org.clauseway.functional.algebra.CheckedBy;

// the claim anchors at the leaf's enclosing class: LawCoverage matches
// exercised samples by enclosure, and the identity under test — equals,
// hashCode, the atom leq — is final on Propagator, so exercising the test
// leaf exercises exactly the base's semantics
@LawsFor(TestPropagators.class)
public class PropagatorLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawChecker.of(CheckedBy.class).verifyClaimsExercised(PropagatorLawsTest.class);
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
		return TestPropagators.of(FlatConstraints.empty(), name,
				Collections.singletonList(term),
				(watched, state) -> Verdict.keep());
	}
}
