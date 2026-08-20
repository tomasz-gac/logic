package com.tgac.logic.tabling;

// ABOUTME: F1's sharpness receipt, settled by F3: Residues compares by theory
// ABOUTME: covering BY CONSTRUCTION — this pins the covering order itself.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.nogoods.NogoodTestAccess;
import com.tgac.logic.unification.Unifiable;
import org.junit.Test;

public class CarrierSharpnessTest {

	@Test
	public void theCoveringOrderOnNogoodTheories() {
		// the order Residues compares entries by, pinned at its sharp points:
		// knowledge entails ⊤, ⊤ entails nothing, and a separate pair of
		// nogoods entails their conjunct nogood (¬A covers ¬(A ∧ B)) — the
		// direction subsumption deletion leans on
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Object top = NogoodTestAccess.of();
		Object aboutX = NogoodTestAccess.of(Posting.bind(x, lval(1)));
		Object conjunct = NogoodTestAccess.of(
				Posting.all(Posting.bind(x, lval(1)), Posting.bind(y, lval(2))));
		Object separate = NogoodTestAccess.of(
				Posting.bind(x, lval(1)), Posting.bind(y, lval(2)));

		assertThat(NogoodTestAccess.theoryLeq(aboutX, top)).isTrue();
		assertThat(NogoodTestAccess.theoryLeq(top, aboutX)).isFalse();
		assertThat(NogoodTestAccess.theoryLeq(aboutX, conjunct)).isTrue();
		assertThat(NogoodTestAccess.theoryLeq(conjunct, aboutX)).isFalse();
		assertThat(NogoodTestAccess.theoryLeq(separate, conjunct)).isTrue();
		assertThat(NogoodTestAccess.theoryLeq(conjunct, separate)).isFalse();
		assertThat(NogoodTestAccess.theoryLeq(separate, aboutX)).isTrue();
	}
}
