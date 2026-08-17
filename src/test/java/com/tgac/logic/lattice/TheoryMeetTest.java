package com.tgac.logic.lattice;

// ABOUTME: The capability meet: slot-mate atoms (same name, same watched
// ABOUTME: surface) that declare Semilattice combine; everything else unions.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TheoryMeetTest {

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();

	private static Imposition<FlatSet, FlatConstraints> on(Unifiable<Integer> target, Object... values) {
		return new Imposition<>(FlatConstraints.class, target, FlatSet.of(values));
	}

	@Test
	public void sameTargetImpositionsFuseToTheirDomainMeet() {
		Theory<FlatConstraints> met = Theory
				.of(Collections.singletonList(on(X, 1, 2)))
				.meet(Theory.of(Collections.singletonList(on(X, 2, 3))));
		assertThat(met.atoms()).containsExactly(on(X, 2));
	}

	@Test
	public void constructionDigestsCollidingAtomsTheSameWay() {
		// normal form is construction-invariant: of() digests like meet()
		Theory<FlatConstraints> built = Theory.of(Arrays.asList(on(X, 1, 2), on(X, 2, 3)));
		assertThat(built.atoms()).containsExactly(on(X, 2));
	}

	@Test
	public void differentTargetsUnion() {
		Theory<FlatConstraints> met = Theory
				.of(Collections.singletonList(on(X, 1, 2)))
				.meet(Theory.of(Collections.singletonList(on(Y, 2, 3))));
		assertThat(met.atoms()).containsExactlyInAnyOrder(on(X, 1, 2), on(Y, 2, 3));
	}

	@Test
	public void disjointDomainsFuseToBottomWhichIsALegalPlanValue() {
		// ⊥ is knowledge (this branch's knowledge is refutational); only
		// execution reads it as failure
		Theory<FlatConstraints> met = Theory
				.of(Collections.singletonList(on(X, 1)))
				.meet(Theory.of(Collections.singletonList(on(X, 2))));
		assertThat(met.atoms()).hasSize(1);
		assertThat(((FlatSet) met.atoms().head().payload()).isAbsorbing()).isTrue();
	}

	@Test
	public void atomsWithoutTheCapabilityUnionEvenOnACollidingSurface() {
		// same watched surface, different names: a propagator never fuses —
		// only family code (the atom kind) declares combinability
		Propagator<FlatConstraints> even = Propagator.of(FlatConstraints.class, "even",
				Collections.singletonList(X), (watched, state) -> Verdict.keep());
		Propagator<FlatConstraints> odd = Propagator.of(FlatConstraints.class, "odd",
				Collections.singletonList(X), (watched, state) -> Verdict.keep());
		Theory<FlatConstraints> met = Theory
				.of(Collections.singletonList((Atom<FlatConstraints>) even))
				.meet(Theory.of(Collections.singletonList((Atom<FlatConstraints>) odd)));
		assertThat(met.atoms()).containsExactlyInAnyOrder(even, odd);
	}

	@Test
	public void impositionTheoriesStayLawfulUnderFusion() {
		java.util.List<Theory<FlatConstraints>> samples = Arrays.asList(
				Theory.empty(),
				Theory.of(Collections.singletonList(on(X, 1, 2))),
				Theory.of(Collections.singletonList(on(X, 2))),
				Theory.of(Arrays.asList(on(X, 1, 2), on(Y, 5))));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
