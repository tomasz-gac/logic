package com.tgac.logic.lattice;

// ABOUTME: The wholesale absorb door over Theory: seeding from the atoms' own
// ABOUTME: empty, one meet + one normalize, the covering guard, unit = success.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class AbsorbTheoryTest {

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();

	private static Imposition<FlatSet, FlatConstraints> on(Unifiable<Integer> target, Object... values) {
		return new Imposition<>(FlatConstraints.class, target, FlatSet.of(values), FlatConstraints.empty());
	}

	private static Package absorbed(Theory<FlatConstraints> theory, Package into) {
		return new BreadthFirstScheduler<>(Trial.imposed(Propagation.absorb(theory), into)).get().head();
	}

	private static FlatConstraints store(Package state) {
		return Constraint.in(state, FlatConstraints.class).get().getFactor();
	}

	@Test
	public void absorbSeedsTheAbsentFamilyFromTheAtomsEmpty() {
		Theory<FlatConstraints> theory = Theory.of(Arrays.asList(on(X, 1, 2), on(Y, 5, 6)));

		Package state = absorbed(theory, Package.empty());

		assertThat(store(state).getValue((Term<?>) X).get()).isEqualTo(FlatSet.of(1, 2));
		assertThat(store(state).getValue((Term<?>) Y).get()).isEqualTo(FlatSet.of(5, 6));
	}

	@Test
	public void absorbMeetsResidentKnowledge() {
		Package seeded = absorbed(Theory.of(Collections.singletonList(on(X, 1, 2, 3))), Package.empty());

		Package state = absorbed(Theory.of(Collections.singletonList(on(X, 2, 3, 4))), seeded);

		assertThat(store(state).getValue((Term<?>) X).get()).isEqualTo(FlatSet.of(2, 3));
	}

	@Test
	public void aSingletonMeetStaysResidentUntilItsVariableIsTouched() {
		// the meet {1,2} ∧ {2,3} is the singleton {2}: the wholesale door
		// meets and re-normalizes, and wholesale normalize skips live-var
		// entries — the point stays a domain until a binding or enforcement
		// touches X (the stated door, whose update routing collapses
		// eagerly, differs here by design)
		Package seeded = absorbed(Theory.of(Collections.singletonList(on(X, 1, 2))), Package.empty());

		Package state = absorbed(Theory.of(Collections.singletonList(on(X, 2, 3))), seeded);

		assertThat(store(state).getValue((Term<?>) X).get()).isEqualTo(FlatSet.of(2));
	}

	@Test
	public void absorbParksPropagatorAtoms() {
		Propagator<FlatConstraints> even = Propagator.of(FlatConstraints.empty(), "even",
				Collections.singletonList(X), (watched, state) -> Verdict.keep());

		Package state = absorbed(Theory.of(
				Collections.singletonList((Atom<FlatConstraints>) even)), Package.empty());

		assertThat(store(state).theory().atoms()).contains(even);
	}

	@Test
	public void theEmptyTheoryAbsorbsAsSuccess() {
		Package state = absorbed(Theory.empty(), Package.empty());
		assertThat(state.getStores().containsKey(FlatConstraints.class)).isFalse();
	}

	@Test
	public void aCoveredTheoryIsSkippedWholesale() {
		// the covering door guard: the resident already entails the incoming
		// knowledge — no meet, no re-normalization, the package rides through
		// untouched
		Package seeded = absorbed(Theory.of(Collections.singletonList(on(X, 1, 2))), Package.empty());

		Package state = absorbed(Theory.of(Collections.singletonList(on(X, 0, 1, 2, 3))), seeded);

		assertThat(state).isSameAs(seeded);
	}
}
