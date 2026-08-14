package com.tgac.logic.constraints;

// ABOUTME: The vocabulary's default doom is the trial's oracle: refuted-if-Done
// ABOUTME: claims doom, everything the trial cannot decide synchronously claims nothing.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Unifiable;
import org.junit.Test;

public class PostingDoomTest {

	private static Package bound(Unifiable<Integer> x, int value) {
		return Trial.imposed(Posting.bind(x, lval(value)), Package.empty())
				.get().head();
	}

	@Test
	public void aContradictedResolutionIsDoomed() {
		// no door passed a doom check: the default must see it through the trial
		Unifiable<Integer> x = lvar();
		Posting resolution = Propagation.resolve(Prefix.binding(
				Package.empty().substitution(), (LVar<Integer>) x.asVar().get(), lval(1)).get());

		assertThat(resolution.doomed(bound(x, 2))).isTrue();
		assertThat(resolution.answers(bound(x, 2))).isZero();
	}

	@Test
	public void anOpenResolutionClaimsNothing() {
		Unifiable<Integer> x = lvar();
		Posting resolution = Propagation.resolve(Prefix.binding(
				Package.empty().substitution(), (LVar<Integer>) x.asVar().get(), lval(1)).get());

		assertThat(resolution.doomed(Package.empty())).isFalse();
	}

	@Test
	public void aJointlyContradictedConjunctionIsDoomed() {
		// the parts are individually fine; only the threaded trial sees the clash
		Unifiable<Integer> x = lvar();
		Posting joint = Posting.all(x.unifies(1), x.unifies(2));

		assertThat(joint.doomed(Package.empty())).isTrue();
	}
}
