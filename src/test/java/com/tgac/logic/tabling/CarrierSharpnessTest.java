package com.tgac.logic.tabling;

// ABOUTME: Stage F's sharpness receipt: the factor order Residues compares by
// ABOUTME: IS theory covering — identical on live factors, ⊥ guarded and keyless.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.nogoods.NogoodTestAccess;
import com.tgac.logic.unification.Unifiable;
import org.junit.Test;

public class CarrierSharpnessTest {

	@Test
	public void factorOrderIsTheoryCoveringOnLiveNogoodFactors() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Object[] samples = {
				NogoodTestAccess.of(),
				NogoodTestAccess.of(Posting.bind(x, lval(1))),
				NogoodTestAccess.of(Posting.all(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)))),
				NogoodTestAccess.of(Posting.bind(x, lval(1)), Posting.bind(y, lval(2))),
		};
		for (Object a : samples) {
			for (Object b : samples) {
				assertThat(NogoodTestAccess.factorLeq(a, b))
						.as("factor leq == theory covering for %s vs %s", a, b)
						.isEqualTo(NogoodTestAccess.theoryLeq(a, b));
			}
		}
	}
}
