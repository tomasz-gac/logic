package com.tgac.logic.nogoods;

// ABOUTME: Partial-order laws for Nogood's atom leq — subsumption: ¬(A)
// ABOUTME: entails ¬(A ∧ B), literal-subset over the flattened conjunct.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Nogood.class)
public class NogoodLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(NogoodLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Unifiable<Integer> Z = lvar();
	private static final Posting A = Posting.bind(X, lval(1));
	private static final Posting B = Posting.bind(Y, lval(2));
	private static final Posting C = Posting.bind(Z, lval(3));

	@Test
	public void subsumptionIsAPartialOrder() {
		java.util.List<Atom<NogoodConstraints>> samples = Arrays.asList(
				Nogood.of(A),
				Nogood.of(B),
				Nogood.of(Posting.all(A, B)),
				Nogood.of(Posting.all(A, B, C)));
		PartialOrderLaws.check(samples);
	}

	@Test
	public void aNogoodEntailsEveryWideningOfItsConjunct() {
		Nogood notA = Nogood.of(A);
		Nogood notAB = Nogood.of(Posting.all(A, B));
		assertThat(notA.leq(notAB)).isTrue();
		assertThat(notAB.leq(notA)).isFalse();
	}
}
