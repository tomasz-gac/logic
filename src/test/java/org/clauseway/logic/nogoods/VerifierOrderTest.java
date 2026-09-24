package org.clauseway.logic.nogoods;

// ABOUTME: Pins verifier-last: a trial-based store folds after every value family,
// ABOUTME: so its verification never samples a mid-trigger un-revised base.

import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.TestSchedulers;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.unification.terms.Unifiable;
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
		assertThat(dom(x, Longs.range(1, 5))
				.and(exclude(dom(x, Longs.range(2, 4))))
				.and(x.unifies(2L))
				.solve(x, TestSchedulers.factory())
				.count()).isZero();

		Unifiable<Long> y = lvar();
		assertThat(dom(y, Longs.range(1, 5))
				.and(exclude(dom(y, Longs.range(2, 4))))
				.and(y.unifies(1L))
				.solve(y, TestSchedulers.factory())
				.count()).isEqualTo(1);
	}

	@Test
	public void theVetoFiresWhenTheVerifierRegistersFirst() {
		// the exclusion registers the nogood family before dom registers FD:
		// the fold visits the verifier first unless the driver defers it
		Unifiable<Long> x = lvar();
		assertThat(exclude(dom(x, Longs.range(2, 4)))
				.and(dom(x, Longs.range(1, 5)))
				.and(x.unifies(2L))
				.solve(x, TestSchedulers.factory())
				.count()).isZero();

		Unifiable<Long> y = lvar();
		assertThat(exclude(dom(y, Longs.range(2, 4)))
				.and(dom(y, Longs.range(1, 5)))
				.and(y.unifies(1L))
				.solve(y, TestSchedulers.factory())
				.count()).isEqualTo(1);
	}
}
