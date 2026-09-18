package com.tgac.logic.finitedomain;

// ABOUTME: Pins store-sighted post pricing: a post disjoint with the live domain
// ABOUTME: prices 0, an overlapping one 1, and blindness (no store) stays 1.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.unification.Unifiable;
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
