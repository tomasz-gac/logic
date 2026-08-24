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

	private static Theory<FlatConstraints> theory(Package state) {
		return Constraint.in(state, FlatConstraints.class).get().getTheory();
	}

	private static FlatSet value(Package state, Unifiable<Integer> target) {
		return FlatConstraints.empty().getValue(theory(state), (Term<?>) target).get();
	}

	@Test
	public void absorbSeedsTheAbsentFamilyFromTheAtomsEmpty() {
		Theory<FlatConstraints> theory = Theory.of(Arrays.asList(on(X, 1, 2), on(Y, 5, 6)));

		Package state = absorbed(theory, Package.empty());

		assertThat(value(state, X)).isEqualTo(FlatSet.of(1, 2));
		assertThat(value(state, Y)).isEqualTo(FlatSet.of(5, 6));
	}

	@Test
	public void absorbMeetsResidentKnowledge() {
		Package seeded = absorbed(Theory.of(Collections.singletonList(on(X, 1, 2, 3))), Package.empty());

		Package state = absorbed(Theory.of(Collections.singletonList(on(X, 2, 3, 4))), seeded);

		assertThat(value(state, X)).isEqualTo(FlatSet.of(2, 3));
	}

	@Test
	public void aSingletonMeetCollapsesEagerlyOnEveryDoor() {
		// the meet {1,2} ∧ {2,3} is the singleton {2}: with the rows merged
		// there is ONE statement semantics — update's routing collapses a
		// point to its binding on every door, and the spent entry drops
		// (the stated/absorb asymmetry was ruled out with the merge)
		Package seeded = absorbed(Theory.of(Collections.singletonList(on(X, 1, 2))), Package.empty());

		Package state = absorbed(Theory.of(Collections.singletonList(on(X, 2, 3))), seeded);

		assertThat(state.substitution().walk((Term<?>) X).get()).isEqualTo(2);
		assertThat(FlatConstraints.empty().getValue(theory(state), (Term<?>) X).isDefined())
				.isFalse();
	}

	@Test
	public void absorbParksPropagatorAtoms() {
		Propagator<FlatConstraints> even = Propagator.of(FlatConstraints.empty(), "even",
				Collections.singletonList(X), (watched, state) -> Verdict.keep());

		Package state = absorbed(Theory.of(
				Collections.singletonList((Atom<FlatConstraints>) even)), Package.empty());

		assertThat(theory(state).atoms()).contains(even);
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
