package com.tgac.logic.lattice;

// ABOUTME: The package's constraint entry: Constraint{Theory, Factor} — knowledge
// ABOUTME: outside, behavior beside it; identity is the Theory half alone.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Unifiable;
import java.util.Collections;
import org.junit.Test;

public class ConstraintTest {

	private static final Unifiable<Integer> X = lvar();

	private static Theory<FlatConstraints> on(Object... values) {
		return Theory.of(Collections.singletonList(
				new Imposition<>(FlatConstraints.class, X, FlatSet.of(values), FlatConstraints.empty())));
	}

	@Test
	public void aConstraintPairsKnowledgeWithItsInterpreter() {
		Theory<FlatConstraints> theory = on(1, 2);
		FlatConstraints factor = FlatConstraints.empty();

		Constraint<FlatConstraints> entry = Constraint.of(theory, factor);

		assertThat(entry.getTheory()).isSameAs(theory);
		assertThat(entry.getFactor()).isSameAs(factor);
	}

	@Test
	public void identityIsTheTheoryHalfAlone() {
		// crossings and marshal read only the knowledge; the factor half is
		// behavior plus a droppable memo — two entries with one theory are
		// one constraint
		Constraint<FlatConstraints> a = Constraint.of(on(1, 2), FlatConstraints.empty());
		Constraint<FlatConstraints> b = Constraint.of(on(1, 2), FlatConstraints.empty());

		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
		assertThat(a).isNotEqualTo(Constraint.of(on(1, 2, 3), FlatConstraints.empty()));
	}
}
