package org.clauseway.logic.nogoods;

// ABOUTME: Lattice laws for the nogood store: meet is nogood union, so more
// ABOUTME: nogoods = lower — claimed for the coverage gate.

import org.clauseway.logic.constraints.Posting;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.algebra.laws.SemilatticeLaws;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.unification.terms.Unifiable;
import io.vavr.collection.LinkedHashSet;
import java.util.Arrays;
import org.junit.Test;

public class NogoodConstraintsLawsTest {


	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Nogood X_APART = Nogood.of(Posting.bind(X, lval(1)));
	private static final Nogood Y_APART = Nogood.of(Posting.bind(Y, lval(2)));
	private static final Nogood NOT_BOTH = Nogood.of(Posting.all(
			Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));

	@Test
	public void theoryRoundTripRebuildsThroughTheStatementDoor() {
		// the crossing there and back, pure for nogoods (the theory IS the bag):
		// stating each atom through the door's meet rebuilds the original —
		// exact on a subsumption-free theory (the crossing deletes dominated atoms)
		Theory<NogoodConstraints> theory = Theory.of(LinkedHashSet.of(X_APART, Y_APART));
		Theory<NogoodConstraints> rebuilt = theory.atoms()
				.foldLeft(Theory.<NogoodConstraints> empty(),
						(t, atom) -> t.meet(Theory.of(LinkedHashSet.of((Nogood) atom))));
		assertThat(rebuilt).isEqualTo(theory);
	}

	@Test
	public void theStoreDeletesDominatedNogoods() {
		// subsumption deletion, live in the theory: ¬(x≡1) states everything
		// ¬(x≡1 ∧ y≡2) does, so the dominated resident drops — fewer trials
		// per revision, same knowledge
		assertThat(Theory.of(LinkedHashSet.of(X_APART, NOT_BOTH)))
				.isEqualTo(Theory.of(LinkedHashSet.of(X_APART)));
	}

	@Test
	public void theStoreOrderIsCoveringNotContainment() {
		// {¬(x≡1)} entails {¬(x≡1 ∧ y≡2)} with no shared residents at all
		Theory<NogoodConstraints> stronger = Theory.of(LinkedHashSet.of(X_APART));
		Theory<NogoodConstraints> weaker = Theory.of(LinkedHashSet.of(NOT_BOTH));
		assertThat(stronger.leq(weaker)).isTrue();
		assertThat(weaker.leq(stronger)).isFalse();
	}

	@Test
	public void nogoodUnionIsAMeetSemilattice() {
		java.util.List<Theory<NogoodConstraints>> samples = Arrays.asList(
				Theory.<NogoodConstraints> empty(),
				Theory.of(LinkedHashSet.of(X_APART)),
				Theory.of(LinkedHashSet.of(Y_APART, NOT_BOTH)),
				Theory.of(LinkedHashSet.of(X_APART, NOT_BOTH)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
