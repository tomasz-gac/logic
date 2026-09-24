package org.clauseway.logic.finitedomain;

// ABOUTME: The trial's Absorption row through the solve pipeline: an excluded
// ABOUTME: FD factor read three ways — refuted discharges, entailed fails, owed carves.

import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.TestSchedulers;
import org.clauseway.logic.constraints.Propagation;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The one nogood literal shape no front door mints today: a bare factor
 * absorption ({@code Propagation.absorb}) inside an exclusion. Every FD door
 * is an Activation, so these receipts build the factor directly — the shape
 * tabled replay ({@code Residues.restated}) produces — and pin the trial's
 * three readings of it.
 */
public class ExcludedFactorTest {

	private static LVar<?> varOf(Unifiable<?> u) {
		return (LVar<?>) u.asVar().get();
	}

	@Test
	public void anExcludedFactorDischargesWhenTheBaseRefutesIt() {
		// refuted reading: the excluded factor's imposition fails against
		// the base — ¬(x ∈ 1..2) with x = 7 discharges, the answer flows
		Unifiable<Long> x = lvar();
		Theory<FiniteDomainConstraints> factor = FiniteDomainConstraints.withDomain(
				Theory.empty(), varOf(x), Longs.range(1, 3));

		List<Long> answers = x.unifies(7L)
				.and(exclude(Propagation.absorb(factor)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(7L);
	}

	@Test
	public void anExcludedFactorViolatesWhenTheBaseEntailsIt() {
		// entailed reading: the base already sits inside the excluded
		// factor — meeting it adds nothing, the sole literal is entailed,
		// every branch fails
		Unifiable<Long> x = lvar();
		Theory<FiniteDomainConstraints> factor = FiniteDomainConstraints.withDomain(
				Theory.empty(), varOf(x), Longs.range(1, 11));

		Goal g = dom(x, Longs.range(2, 5))
				.and(exclude(Propagation.absorb(factor)));
		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void anExcludedFactorStaysOwedAndCarvesAtLabelling() {
		// owed reading: ¬(x ∈ 2..4) as a factor literal over x ∈ 0..6
		// brings new knowledge at registration, so the nogood stays; the
		// ground floor excludes exactly the factor's region
		Unifiable<Long> x = lvar();
		Theory<FiniteDomainConstraints> factor = FiniteDomainConstraints.withDomain(
				Theory.empty(), varOf(x), Longs.range(2, 5));

		List<Long> answers = dom(x, Longs.range(0, 7))
				.and(exclude(Propagation.absorb(factor)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(0L, 1L, 5L, 6L);
	}
}
