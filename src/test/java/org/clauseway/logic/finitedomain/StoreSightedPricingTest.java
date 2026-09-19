package org.clauseway.logic.finitedomain;

// ABOUTME: Pins store-sighted post pricing: a post disjoint with the live domain
// ABOUTME: prices 0, an overlapping one 1, and blindness (no store) stays 1.

import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.goals.optimizer.Bounded;
import org.clauseway.logic.unification.Unifiable;
import org.junit.Test;

public class StoreSightedPricingTest {

	@Test
	public void disjointPostPricesZero() {
		Unifiable<Long> x = lvar();
		Goal post = FiniteDomain.dom(x, Longs.interval(8, 12));
		assertThat(((Bounded) post).answers(FiniteDomainTestSupport.withDomain(x, Longs.interval(0, 4))))
				.isEqualTo(0);
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
