package com.tgac.logic.nogoods;

// ABOUTME: Lattice laws for Theory — the plan-space value: meet is atom union,
// ABOUTME: leq the covering order, sharp exactly as far as atom leq reaches.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.Imposition;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Theory.class)
public class TheoryLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(TheoryLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Nogood NOT_A = Nogood.of(Posting.bind(X, lval(1)));
	private static final Nogood NOT_B = Nogood.of(Posting.bind(Y, lval(2)));
	private static final Nogood NOT_AB = Nogood.of(Posting.all(
			Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));

	@Test
	public void theoriesFormAMeetSemilatticeWithTheCoveringOrder() {
		java.util.List<Theory<NogoodConstraints>> samples = Arrays.asList(
				Theory.empty(),
				Theory.of(Collections.singletonList(NOT_A)),
				Theory.of(Arrays.asList(NOT_B, NOT_AB)),
				Theory.of(Arrays.asList(NOT_A, NOT_B)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}

	@Test
	public void meetDeletesStrictlyDominatedAtoms() {
		// subsumption deletion: ¬A states everything ¬(A ∧ B) does — this is
		// the dedup that makes the covering order agree with accumulation
		Theory<NogoodConstraints> met = Theory
				.of(Collections.singletonList(NOT_AB))
				.meet(Theory.of(Collections.singletonList(NOT_A)));
		assertThat(met.atoms()).containsExactly(NOT_A);
	}

	@Test
	public void sameSurfaceNogoodsFuseIntoOneConjunctionAtom() {
		// same name, same watched surface, different knowledge: the slot
		// holds ONE atom — a nogood holds its conjuncts as a collection, and
		// combine is their union
		Nogood xyOneTwo = Nogood.of(Posting.all(
				Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));
		Nogood xyTwoOne = Nogood.of(Posting.all(
				Posting.bind(X, lval(2)), Posting.bind(Y, lval(1))));
		Theory<NogoodConstraints> met = Theory
				.of(Collections.singletonList(xyOneTwo))
				.meet(Theory.of(Collections.singletonList(xyTwoOne)));
		assertThat(met.atoms()).containsExactly(xyOneTwo.combine(xyTwoOne));
		assertThat(((Nogood) met.atoms().head()).getForbidden())
				.isEqualTo(xyOneTwo.getForbidden().addAll(xyTwoOne.getForbidden()));
	}

	@Test
	public void theCoveringOrderIsSharperThanSubsetThroughAtomLeq() {
		// ¬A entails ¬(A ∧ B): a theory holding the stronger atom covers a
		// theory holding the weaker one, with no shared atoms at all
		Theory<NogoodConstraints> stronger =
				Theory.of(Collections.singletonList(NOT_A));
		Theory<NogoodConstraints> weaker =
				Theory.of(Collections.singletonList(NOT_AB));
		assertThat(stronger.leq(weaker)).isTrue();
		assertThat(weaker.leq(stronger)).isFalse();
	}
}
