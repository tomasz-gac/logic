package org.clauseway.logic.finitedomain;

// ABOUTME: Pins that post pricing is a count, never a verdict: a post prices 1
// ABOUTME: with or without a store, even when the live domain refutes it.

import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.goals.optimizer.Bounded;
import org.clauseway.logic.unification.Unifiable;
import org.junit.Test;

public class PostPricingTest {

	@Test
	public void aRefutedPostStillPricesOne() {
		// the live domain is disjoint with the post: that is doom (the
		// pruning pass's diet), not a count — the price does not flinch
		Unifiable<Long> x = lvar();
		Goal post = FiniteDomain.dom(x, Longs.interval(8, 12));
		assertThat(((Bounded) post).answers(FiniteDomainTestSupport.withDomain(x, Longs.interval(0, 4))))
				.isEqualTo(1);
	}

	@Test
	public void overlappingPostPricesOne() {
		Unifiable<Long> x = lvar();
		Goal post = FiniteDomain.dom(x, Longs.interval(3, 12));
		assertThat(((Bounded) post).answers(FiniteDomainTestSupport.withDomain(x, Longs.interval(0, 4))))
				.isEqualTo(1);
	}

	@Test
	public void withoutAStoreThePostStaysBlind() {
		Unifiable<Long> x = lvar();
		Goal post = FiniteDomain.dom(x, Longs.interval(8, 12));
		assertThat(((Bounded) post).answers(Package.empty())).isEqualTo(1);
	}
}
