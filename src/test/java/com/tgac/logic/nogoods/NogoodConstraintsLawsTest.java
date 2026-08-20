package com.tgac.logic.nogoods;

// ABOUTME: Lattice laws for the nogood store: meet is nogood union, so more
// ABOUTME: nogoods = lower — claimed for the coverage gate.

import com.tgac.logic.constraints.Posting;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.LinkedHashSet;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Theory.class)
public class NogoodConstraintsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(NogoodConstraintsLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Nogood X_APART = Nogood.of(Posting.bind(X, lval(1)));
	private static final Nogood Y_APART = Nogood.of(Posting.bind(Y, lval(2)));
	private static final Nogood NOT_BOTH = Nogood.of(Posting.all(
			Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));

	@Test
	public void theoryRoundTripRebuildsTheFactor() {
		// the crossing there and back, pure for nogoods (the factor IS the bag):
		// theory() → fold meet(Atom) into the empty family ≡ the original —
		// exact on a subsumption-free factor (the crossing deletes dominated atoms)
		NogoodConstraints factor = NogoodConstraints.of(
				LinkedHashSet.of(X_APART, Y_APART));
		NogoodConstraints rebuilt = factor.theory().atoms()
				.foldLeft(NogoodConstraints.EMPTY, NogoodConstraints::meet);
		assertThat(rebuilt).isEqualTo(factor);
	}

	@Test
	public void theStoreDeletesDominatedNogoods() {
		// subsumption deletion, live in the store: ¬(x≡1) states everything
		// ¬(x≡1 ∧ y≡2) does, so the dominated resident drops — fewer trials
		// per revision, same knowledge
		assertThat(NogoodConstraints.of(LinkedHashSet.of(X_APART, NOT_BOTH)))
				.isEqualTo(NogoodConstraints.of(LinkedHashSet.of(X_APART)));
	}

	@Test
	public void theStoreOrderIsCoveringNotContainment() {
		// {¬(x≡1)} entails {¬(x≡1 ∧ y≡2)} with no shared residents at all
		NogoodConstraints stronger = NogoodConstraints.of(LinkedHashSet.of(X_APART));
		NogoodConstraints weaker = NogoodConstraints.of(LinkedHashSet.of(NOT_BOTH));
		assertThat(stronger.theory().leq(weaker.theory())).isTrue();
		assertThat(weaker.theory().leq(stronger.theory())).isFalse();
	}

	@Test
	public void nogoodUnionIsAMeetSemilattice() {
		java.util.List<Theory<NogoodConstraints>> samples = Arrays.asList(
				NogoodConstraints.of(LinkedHashSet.empty()).theory(),
				NogoodConstraints.of(LinkedHashSet.of(X_APART)).theory(),
				NogoodConstraints.of(LinkedHashSet.of(Y_APART, NOT_BOTH)).theory(),
				NogoodConstraints.of(LinkedHashSet.of(X_APART, NOT_BOTH)).theory());
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
