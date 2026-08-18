package com.tgac.logic.lattice;

// ABOUTME: The covering door guard: absorbing content the resident already
// ABOUTME: entails is a no-op — no meet, no re-normalization queued.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.Utils;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import org.junit.Test;

public class AbsorbGuardTest {

	@Test
	public void absorbingCoveredContentLeavesThePackageUntouched() {
		Unifiable<Integer> x = lvar();
		FlatConstraints store = FlatConstraints.empty().withValue(x, FlatSet.of(1, 2));

		List<Package> seeded = Utils.collect(Propagation.absorb(store).apply(Package.empty()));
		assertThat(seeded).hasSize(1);

		// the same knowledge arrives again: the resident covers it, so the
		// door neither meets nor queues normalize — the package rides through
		List<Package> again = Utils.collect(Propagation.absorb(store).apply(seeded.get(0)));
		assertThat(again).hasSize(1);
		assertThat(again.get(0)).isSameAs(seeded.get(0));
	}

	@Test
	public void absorbingWiderContentIsAlsoCovered() {
		Unifiable<Integer> x = lvar();
		FlatConstraints narrow = FlatConstraints.empty().withValue(x, FlatSet.of(1));
		FlatConstraints wide = FlatConstraints.empty().withValue(x, FlatSet.of(1, 2));

		Package seeded = Utils.collect(Propagation.absorb(narrow).apply(Package.empty())).get(0);
		// {x⊂{1}} entails {x⊂{1,2}}: covered, skipped
		Package again = Utils.collect(Propagation.absorb(wide).apply(seeded)).get(0);
		assertThat(again).isSameAs(seeded);
	}
}
