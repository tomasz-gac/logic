package com.tgac.logic.nogoods;

// ABOUTME: The trial's edges: a woken suspension may legally fork an imposition,
// ABOUTME: and double negation decides at the ground floor without eager narrowing.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
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
				.get().get(0);

		io.vavr.collection.List<Package> worlds = Verification.imposed(y.unifies(5), state).get();
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

		java.util.List<Long> answers = dom(x, EnumeratedDomain.range(5L, 9L))
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
		Posting inner = exclude(dom(x, EnumeratedDomain.range(0L, 5L)));

		Goal g = dom(x, EnumeratedDomain.range(0L, 10L))
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
				.and(exclude(exclude(dom(x, EnumeratedDomain.range(0L, 5L)))));
		assertThat(violated.solve(x, TestSchedulers.factory()).count()).isZero();

		Unifiable<Long> y = lvar();
		Goal satisfied = y.unifies(3L)
				.and(exclude(exclude(dom(y, EnumeratedDomain.range(0L, 5L)))));
		assertThat(satisfied.solve(y, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(3L);
	}
}
