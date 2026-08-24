package com.tgac.logic.lattice;

// ABOUTME: The covering door guard: absorbing content the resident already
// ABOUTME: entails is a no-op — no meet, no re-normalization queued.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.Utils;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Package;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import org.junit.Test;

public class AbsorbGuardTest {

	@Test
	public void absorbingCoveredContentLeavesThePackageUntouched() {
		Unifiable<Integer> x = lvar();
		Theory<FlatConstraints> store = LatticeFactorTest.valued((Term<?>) x, 1, 2);

		List<Package> seeded = Utils.collect(Propagation.absorb(store).apply(Package.empty()));
		assertThat(seeded).hasSize(1);

		// the same knowledge arrives again: the resident covers it, so the
		// door neither meets nor queues normalize — the package rides through
		List<Package> again = Utils.collect(Propagation.absorb(store).apply(seeded.get(0)));
		assertThat(again).hasSize(1);
		assertThat(again.get(0)).isSameAs(seeded.get(0));
	}

	@Test
	public void absorbingContentEntailedByABindingLandsEqual() {
		// {x⊂{1}} collapses eagerly to x=1 and the entry spends — the
		// knowledge now lives in the SUBSTITUTION, where the theory-level
		// covering guard cannot see it. The wider absorb re-verifies against
		// the binding and lands EQUAL (the trial's classifier reads equality,
		// not identity); only theory-covered content skips by identity
		Unifiable<Integer> x = lvar();
		Theory<FlatConstraints> narrow = LatticeFactorTest.valued((Term<?>) x, 1);
		Theory<FlatConstraints> wide = LatticeFactorTest.valued((Term<?>) x, 1, 2);

		Package seeded = Utils.collect(Propagation.absorb(narrow).apply(Package.empty())).get(0);
		Package again = Utils.collect(Propagation.absorb(wide).apply(seeded)).get(0);
		assertThat(again).isEqualTo(seeded);
	}
}
