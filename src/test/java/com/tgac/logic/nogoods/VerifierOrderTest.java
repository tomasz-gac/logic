package com.tgac.logic.nogoods;

// ABOUTME: Pins verifier-last: a trial-based store folds after every value family,
// ABOUTME: so its verification never samples a mid-trigger un-revised base.

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.unification.Unifiable;
import org.junit.Test;

/**
 * The nogood family verifies by TRIAL, which presupposes a base where every
 * value family has finished reacting to the current trigger. Store fold
 * order follows store REGISTRATION order, so the two programs below differ
 * only in which family registers first — and the answer set may not. Before
 * verifier-last, the verifier-first order let an entailed literal fuse into
 * a not-yet-spent domain entry, misread as owed, and the veto never fired —
 * the tabled-exclusion regression, deterministic in this shape.
 */
public class VerifierOrderTest {

	@Test
	public void theVetoFiresWhenTheValueFamilyRegistersFirst() {
		Unifiable<Long> x = lvar();
		assertThat(dom(x, EnumeratedDomain.range(1L, 5L))
				.and(exclude(dom(x, EnumeratedDomain.range(2L, 4L))))
				.and(x.unifies(2L))
				.solve(x, TestSchedulers.factory())
				.count()).isZero();

		Unifiable<Long> y = lvar();
		assertThat(dom(y, EnumeratedDomain.range(1L, 5L))
				.and(exclude(dom(y, EnumeratedDomain.range(2L, 4L))))
				.and(y.unifies(1L))
				.solve(y, TestSchedulers.factory())
				.count()).isEqualTo(1);
	}

	@Test
	public void theVetoFiresWhenTheVerifierRegistersFirst() {
		// the exclusion registers the nogood family before dom registers FD:
		// the fold visits the verifier first unless the driver defers it
		Unifiable<Long> x = lvar();
		assertThat(exclude(dom(x, EnumeratedDomain.range(2L, 4L)))
				.and(dom(x, EnumeratedDomain.range(1L, 5L)))
				.and(x.unifies(2L))
				.solve(x, TestSchedulers.factory())
				.count()).isZero();

		Unifiable<Long> y = lvar();
		assertThat(exclude(dom(y, EnumeratedDomain.range(2L, 4L)))
				.and(dom(y, EnumeratedDomain.range(1L, 5L)))
				.and(y.unifies(1L))
				.solve(y, TestSchedulers.factory())
				.count()).isEqualTo(1);
	}
}
