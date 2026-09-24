package org.clauseway.logic.constraints;

// ABOUTME: The vocabulary's default doom is the trial's oracle: refuted-if-Done
// ABOUTME: claims doom, everything the trial cannot decide synchronously claims nothing.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.Prefix;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;

public class PostingDoomTest {

	private static Package bound(Unifiable<Integer> x, int value) {
		return Trial.imposed(Posting.bind(x, lval(value)), Package.empty())
				.ground().head();
	}

	@Test
	public void aContradictedResolutionIsDoomed() {
		// no door passed a doom check: the default must see it through the trial
		Unifiable<Integer> x = lvar();
		Posting resolution = Propagation.resolve(Prefix.binding(
				Package.empty().substitution(), (LVar<Integer>) x.asVar().get(), lval(1)).get());

		assertThat(resolution.doomed(bound(x, 2))).isTrue();
		// the verdict never leaks into the count
		assertThat(resolution.answers(bound(x, 2))).isEqualTo(1L);
	}

	@Test
	public void anOpenResolutionClaimsNothing() {
		Unifiable<Integer> x = lvar();
		Posting resolution = Propagation.resolve(Prefix.binding(
				Package.empty().substitution(), (LVar<Integer>) x.asVar().get(), lval(1)).get());

		assertThat(resolution.doomed(Package.empty())).isFalse();
	}

	@Test
	public void fluentAndStaysInTheVocabulary() {
		// Posting.and(Posting...) overloads Goal.and(Goal...) — more specific,
		// so the result stays in the vocabulary and the trial's joint doom
		// sees through the chain
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Posting joint = x.unifies(1).and(x.unifies(2));
		assertThat(joint.doomed(Package.empty())).isTrue();

		Posting three = x.unifies(1).and(y.unifies(2), x.unifies(1));
		assertThat(three.doomed(Package.empty())).isFalse();
	}

	@Test
	public void aJointlyContradictedConjunctionIsDoomed() {
		// the parts are individually fine; only the threaded trial sees the clash
		Unifiable<Integer> x = lvar();
		Posting joint = Posting.all(x.unifies(1), x.unifies(2));

		assertThat(joint.doomed(Package.empty())).isTrue();
	}
}
