package com.tgac.logic.finitedomain;

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.leq;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.monad.Cont;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Pins the propagation gaps from docs/design/constraint-kernel.md
 * Each test states which phase of the redesign makes it pass.
 */
public class PropagationPinTest {

	/**
	 * Gap 2 (chokepoint bypass) + committed choice. An FD domain collapse binds via
	 * extendS without waking the Neq store, so mid-search the doomed branch looks
	 * successful; condu commits to it and discards the valid alternative. The
	 * reify-time rescue then kills the committed branch — the query loses the answer
	 * the discarded clause would have produced. Passes after Phase 1.
	 */
	@Test(timeout = 5000)
	public void committedChoiceMustNotCommitToABranchViolatingDisequality() {
		Unifiable<Long> x = lvar();

		// the intersection {1,2} ∩ {1} collapses to a Singleton, which binds x mid-search
		Goal g = Disequality.separate(x, lval(1L))
				.and(dom(x, EnumeratedDomain.range(1L, 3L)))       // x ∈ {1,2}
				.and(Goal.condu(
						dom(x, EnumeratedDomain.range(1L, 2L)),    // ∩ → {1}: collapse-binds, violates x ≠ 1
						dom(x, EnumeratedDomain.range(2L, 3L))));  // ∩ → {2}: the valid branch

		List<Long> result = g.solve(x).map(Term::get).collect(Collectors.toList());

		assertThat(result).containsExactly(2L);
	}

	/**
	 * Gap 3 (narrowing wakes nobody). leq(x,y) runs while y is still wide; when
	 * leq(y,z) later narrows y (to a non-singleton, so no binding occurs), nothing
	 * re-runs leq(x,y), and x's domain keeps values the fixpoint excludes. Passes
	 * after Phase 2 (wake-on-narrowing). See docs/design/constraint-kernel.md
	 */
	@Test(timeout = 5000)
	public void narrowingPropagatesToConstraintsStatedEarlier() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		Package[] beforeLabelling = new Package[1];
		Goal probe = s -> {
			beforeLabelling[0] = s;
			return Cont.just(s);
		};

		long count = dom(x, EnumeratedDomain.range(1L, 11L))        // {1..10}
				.and(dom(y, EnumeratedDomain.range(1L, 11L)))       // {1..10}
				.and(dom(z, EnumeratedDomain.range(1L, 4L)))        // {1..3}
				.and(leq(x, y))                                     // runs while y is wide
				.and(leq(y, z))                                     // narrows y to {1..3}
				.and(probe)
				.solve(x)
				.count();

		assertThat(count).isGreaterThan(0);
		// x stays unbound here (its domain never collapses), so the domain map is live
		assertThat(FiniteDomainConstraints.getDom(beforeLabelling[0], x.asVar().get())
				.get()
				.contains(10L))
				.as("x's domain should exclude 10 before labelling (fixpoint x ≤ 3)")
				.isFalse();
	}

	/** Control: the same query with the constraints in dataflow order reaches the fixpoint today. */
	@Test(timeout = 5000)
	public void narrowingReachesTheFixpointWhenStatedInDataflowOrder() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();
		Package[] beforeLabelling = new Package[1];
		Goal probe = s -> {
			beforeLabelling[0] = s;
			return Cont.just(s);
		};

		long count = dom(x, EnumeratedDomain.range(1L, 11L))
				.and(dom(y, EnumeratedDomain.range(1L, 11L)))
				.and(dom(z, EnumeratedDomain.range(1L, 4L)))
				.and(leq(y, z))                                     // y → {1..3} first
				.and(leq(x, y))                                     // then x ≤ max(y) = 3
				.and(probe)
				.solve(x)
				.count();

		assertThat(count).isGreaterThan(0);
		assertThat(FiniteDomainConstraints.getDom(beforeLabelling[0], x.asVar().get())
				.get()
				.contains(10L))
				.isFalse();
	}
}
