package org.clauseway.logic.nogoods;

// ABOUTME: The trial's edges: a woken suspension may legally fork an imposition,
// ABOUTME: and double negation decides at the ground floor without eager narrowing.

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.constraints.Propagation;
import org.clauseway.logic.constraints.Trial;
import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Exhaustion;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.Test;

public class TrialEdgeTest {

	@Test
	public void aWokenForkingSuspensionForksTheTrialImposition() {
		// the >1-worlds branch's reachability: the statement itself cannot
		// fork, but a resident suspension body can
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Package state = Exhaustion.collected(
						Propagation.suspend(
										Collections.singletonList(y),
										s -> s.walk(y).asVal().isDefined(),
										x.unifies(1).or(x.unifies(2)))
								.apply(Package.empty()))
				.ground().get(0);

		io.vavr.collection.List<Package> worlds = Trial.imposed(y.unifies(5), state).ground();
		assertThat(worlds).hasSize(2);
	}

	@Test
	public void aStoreVetoedBindStaysOwedAndDischargesAtTheGroundFloor() {
		// the substitution trial is store-blind: ¬(x=3) with resident
		// x ∈ 5..8 stays OWED (the package trial would discharge it at first
		// examination through the FD veto) — kept wider, never wrong: every
		// labelled value refutes the bind branch-wise and the full answer
		// set delivers. The eager discharge is the doomed(Package) seam's
		// future earliness, not a soundness need
		Unifiable<Long> x = lvar();

		java.util.List<Long> answers = dom(x, Longs.range(5, 9))
				.and(exclude(x.unifies(3L)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(java.util.stream.Collectors.toList());

		assertThat(answers).containsExactly(5L, 6L, 7L, 8L);
	}

	@Test
	public void doubleNegationDoesNotNarrowEagerly() {
		// ¬¬(x ∈ 0..4) must not become x ∈ 0..4 in the FD store
		Unifiable<Long> x = lvar();
		Posting inner = exclude(dom(x, Longs.range(0, 5)));

		Goal g = dom(x, Longs.range(0, 10))
				.and(exclude(inner));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L);
	}

	@Test
	public void doubleNegationDecidesAtGround() {
		// ¬¬(x ∈ 0..5) with x = 7: the ground floor must fail the branch
		Unifiable<Long> x = lvar();
		Goal violated = x.unifies(7L)
				.and(exclude(exclude(dom(x, Longs.range(0, 5)))));
		assertThat(violated.solve(x, TestSchedulers.factory()).count()).isZero();

		Unifiable<Long> y = lvar();
		Goal satisfied = y.unifies(3L)
				.and(exclude(exclude(dom(y, Longs.range(0, 5)))));
		assertThat(satisfied.solve(y, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(3L);
	}
}
