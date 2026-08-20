package com.tgac.logic.lattice;

// ABOUTME: Theory.stated(): the theory as its own statement — the fold of its
// ABOUTME: atoms' postings; registration seeds absent stores (master seeding).

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.constraints.Trial;
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

public class TheoryStatedTest {

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();

	private static Imposition<FlatSet, FlatConstraints> on(Unifiable<Integer> target, Object... values) {
		return new Imposition<>(FlatConstraints.class, target, FlatSet.of(values), FlatConstraints.empty());
	}

	private static Package stated(Theory<FlatConstraints> theory, Package into) {
		return new BreadthFirstScheduler<>(Trial.imposed(theory.stated(), into)).get().head();
	}

	@Test
	public void statedImposesEveryAtomAndSeedsTheAbsentStore() {
		// the master-seeding receipt: the target package has NO FlatConstraints
		// store — each atom's posting carries its registration seed
		Theory<FlatConstraints> theory = Theory.of(Arrays.asList(on(X, 1, 2), on(Y, 5, 6)));

		Package state = stated(theory, Package.empty());

		FlatConstraints store = (FlatConstraints) state.getStores()
				.get(FlatConstraints.class).get();
		assertThat(store.getValue((Term<?>) X).get()).isEqualTo(FlatSet.of(1, 2));
		assertThat(store.getValue((Term<?>) Y).get()).isEqualTo(FlatSet.of(5, 6));
	}

	@Test
	public void statedMeetsResidentKnowledge() {
		// statement is imposition, not replacement: x ⊂ {1,2,3} already
		// resident, stating x ⊂ {2,3,4} narrows to the meet
		Package seeded = stated(Theory.of(Collections.singletonList(on(X, 1, 2, 3))), Package.empty());

		Package state = stated(Theory.of(Collections.singletonList(on(X, 2, 3, 4))), seeded);

		FlatConstraints store = (FlatConstraints) state.getStores()
				.get(FlatConstraints.class).get();
		assertThat(store.getValue((Term<?>) X).get()).isEqualTo(FlatSet.of(2, 3));
	}

	@Test
	public void statedCollapsesASingletonMeetToItsBinding() {
		// the meet {1,2} ∧ {2,3} is the singleton {2}: the store collapses it
		// to a binding and spends the entry — statement rides the full
		// consume/cascade path, not a raw map write
		Package seeded = stated(Theory.of(Collections.singletonList(on(X, 1, 2))), Package.empty());

		Package state = stated(Theory.of(Collections.singletonList(on(X, 2, 3))), seeded);

		FlatConstraints store = (FlatConstraints) state.getStores()
				.get(FlatConstraints.class).get();
		assertThat(store.getValue((Term<?>) X).isDefined()).isFalse();
		assertThat(state.substitution().walk((Term<?>) X).get()).isEqualTo(2);
	}

	@Test
	public void statedParksPropagatorAtoms() {
		Propagator<FlatConstraints> even = Propagator.of(FlatConstraints.empty(), "even",
				Collections.singletonList(X), (watched, state) -> Verdict.keep());
		Theory<FlatConstraints> theory = Theory.of(
				Collections.singletonList((Atom<FlatConstraints>) even));

		Package state = stated(theory, Package.empty());

		FlatConstraints store = (FlatConstraints) state.getStores()
				.get(FlatConstraints.class).get();
		assertThat(store.theory().atoms()).contains(even);
	}

	@Test
	public void theEmptyTheoryStatesAsSuccess() {
		Package state = stated(Theory.empty(), Package.empty());
		assertThat(state.getStores().containsKey(FlatConstraints.class)).isFalse();
	}
}
