package org.clauseway.logic.finitedomain;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.Cont;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Pins the propagation gaps from docs/reference/constraint-kernel.md
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
		Goal g = exclude(x.unifies(lval(1L)))
				.and(dom(x, Longs.range(1, 3)))       // x ∈ {1,2}
				.and(Goal.condu(
						dom(x, Longs.range(1, 2)),    // ∩ → {1}: collapse-binds, violates x ≠ 1
						dom(x, Longs.range(2, 3))));  // ∩ → {2}: the valid branch

		List<Long> result = g.solve(x, TestSchedulers.factory()).map(Term::get).collect(Collectors.toList());

		assertThat(result).containsExactly(2L);
	}

	/**
	 * Gap 3 (narrowing wakes nobody). leq(x,y) runs while y is still wide; when
	 * leq(y,z) later narrows y (to a non-singleton, so no binding occurs), nothing
	 * re-runs leq(x,y), and x's domain keeps values the fixpoint excludes. Passes
	 * after Phase 2 (wake-on-narrowing). See docs/reference/constraint-kernel.md
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

		long count = dom(x, Longs.range(1, 11))        // {1..10}
				.and(dom(y, Longs.range(1, 11)))       // {1..10}
				.and(dom(z, Longs.range(1, 4)))        // {1..3}
				.and(Longs.leq(x, y))                                     // runs while y is wide
				.and(Longs.leq(y, z))                                     // narrows y to {1..3}
				.and(probe)
				.solve(x, TestSchedulers.factory())
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

		long count = dom(x, Longs.range(1, 11))
				.and(dom(y, Longs.range(1, 11)))
				.and(dom(z, Longs.range(1, 4)))
				.and(Longs.leq(y, z))                                     // y → {1..3} first
				.and(Longs.leq(x, y))                                     // then x ≤ max(y) = 3
				.and(probe)
				.solve(x, TestSchedulers.factory())
				.count();

		assertThat(count).isGreaterThan(0);
		assertThat(FiniteDomainConstraints.getDom(beforeLabelling[0], x.asVar().get())
				.get()
				.contains(10L))
				.isFalse();
	}
}
