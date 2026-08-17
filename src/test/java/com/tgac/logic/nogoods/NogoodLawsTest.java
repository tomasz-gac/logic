package com.tgac.logic.nogoods;

// ABOUTME: Partial-order laws for Nogood's atom leq — subsumption: ¬(A)
// ABOUTME: entails ¬(A ∧ B), literal-subset over the flattened conjunct.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
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
		// includes a permuted pair: mutual entailment must mean equality
		java.util.List<Atom<NogoodConstraints>> samples = Arrays.asList(
				Nogood.of(A),
				Nogood.of(B),
				Nogood.of(Posting.all(A, B)),
				Nogood.of(Posting.all(B, A)),
				Nogood.of(Posting.all(A, B, C)));
		PartialOrderLaws.check(samples);
	}

	@Test
	public void aNogoodIsItsLiteralSetNotItsLiteralOrder() {
		// ∧ is commutative: ¬(A ∧ B) and ¬(B ∧ A) are the same knowledge,
		// so they must be the same nogood — dedup and cross-lineage key
		// comparison depend on it
		Nogood ab = Nogood.of(Posting.all(A, B));
		Nogood ba = Nogood.of(Posting.all(B, A));
		assertThat(ab).isEqualTo(ba);
		assertThat(ab.hashCode()).isEqualTo(ba.hashCode());
	}

	@Test
	public void aNogoodEntailsEveryWideningOfItsConjunct() {
		Nogood notA = Nogood.of(A);
		Nogood notAB = Nogood.of(Posting.all(A, B));
		assertThat(notA.leq(notAB)).isTrue();
		assertThat(notAB.leq(notA)).isFalse();
	}

	@Test
	public void sameSurfaceNogoodsFormAMeetSemilattice() {
		Nogood oneTwo = Nogood.of(Posting.all(
				Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));
		Nogood twoOne = Nogood.of(Posting.all(
				Posting.bind(X, lval(2)), Posting.bind(Y, lval(1))));
		SemilatticeLaws.check(Arrays.asList(oneTwo, twoOne, oneTwo.combine(twoOne)));
	}

	@Test
	public void combineUnionsConjunctsOnASharedSurface() {
		Nogood oneTwo = Nogood.of(Posting.all(
				Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));
		Nogood twoOne = Nogood.of(Posting.all(
				Posting.bind(X, lval(2)), Posting.bind(Y, lval(1))));
		Nogood met = oneTwo.combine(twoOne);
		assertThat(met.getForbidden())
				.isEqualTo(oneTwo.getForbidden().addAll(twoOne.getForbidden()));
		// the conjunction entails each of its parts
		assertThat(met.leq(oneTwo)).isTrue();
		assertThat(met.leq(twoOne)).isTrue();
		assertThat(oneTwo.leq(met)).isFalse();
	}

	@Test
	public void combineRefusesDifferentSurfacesLoudly() {
		assertThatThrownBy(() ->
						Nogood.of(A).combine(Nogood.of(B)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
