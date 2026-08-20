package com.tgac.logic.finitedomain;

// ABOUTME: The trial's Absorption row through the solve pipeline: an excluded
// ABOUTME: FD factor read three ways — refuted discharges, entailed fails, owed carves.

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
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
		FiniteDomainConstraints factor = FiniteDomainConstraints.empty()
				.withDomain(varOf(x), EnumeratedDomain.range(1L, 3L));

		List<Long> answers = x.unifies(7L)
				.and(exclude(Propagation.absorb(factor.theory())))
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
		FiniteDomainConstraints factor = FiniteDomainConstraints.empty()
				.withDomain(varOf(x), EnumeratedDomain.range(1L, 11L));

		Goal g = dom(x, EnumeratedDomain.range(2L, 5L))
				.and(exclude(Propagation.absorb(factor.theory())));
		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void anExcludedFactorStaysOwedAndCarvesAtLabelling() {
		// owed reading: ¬(x ∈ 2..4) as a factor literal over x ∈ 0..6
		// brings new knowledge at registration, so the nogood stays; the
		// ground floor excludes exactly the factor's region
		Unifiable<Long> x = lvar();
		FiniteDomainConstraints factor = FiniteDomainConstraints.empty()
				.withDomain(varOf(x), EnumeratedDomain.range(2L, 5L));

		List<Long> answers = dom(x, EnumeratedDomain.range(0L, 7L))
				.and(exclude(Propagation.absorb(factor.theory())))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(0L, 1L, 5L, 6L);
	}
}
