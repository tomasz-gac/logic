package com.tgac.logic.tabling;

// ABOUTME: Condition's contract: ⊕ is region union in absorption normal form
// ABOUTME: (dominated drops, dominating evicts, 1 absorbs all), ⊗ is factor meet.

import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.collection.HashMap;
import org.junit.Test;

public class ConditionTest {

	private static final Condition WIDE = Condition.of(Span.factor(0L, 10L));
	private static final Condition NARROW = Condition.of(Span.factor(3L, 6L));
	private static final Condition APART = Condition.of(Span.factor(20L, 30L));

	@Test
	public void groundIsOne() {
		assertThat(Condition.of(HashMap.empty())).isSameAs(Condition.ONE);
		assertThat(Condition.ONE.isOne()).isTrue();
		assertThat(WIDE.isOne()).isFalse();
	}

	@Test
	public void aDominatedConjunctIsAbsorbed() {
		assertThat(WIDE.or(NARROW)).isSameAs(WIDE);
	}

	@Test
	public void aDominatingConjunctEvicts() {
		Condition grown = NARROW.or(WIDE);

		assertThat(grown.conjuncts()).hasSize(1);
		assertThat(grown).isEqualTo(WIDE);
		assertThat(grown).isNotEqualTo(NARROW);
	}

	@Test
	public void incomparableConjunctsCoexist() {
		assertThat(NARROW.or(APART).conjuncts()).hasSize(2);
	}

	@Test
	public void oneAbsorbsEverything() {
		// a ground answer arriving after conditional ones: 1 ⊕ a = 1
		assertThat(NARROW.or(APART).or(Condition.ONE)).isEqualTo(Condition.ONE);
		assertThat(Condition.ONE.or(NARROW)).isSameAs(Condition.ONE);
	}

	@Test
	public void zeroIsTheSumIdentity() {
		assertThat(Condition.ZERO.or(NARROW)).isEqualTo(NARROW);
		assertThat(NARROW.or(Condition.ZERO)).isSameAs(NARROW);
	}

	@Test
	public void equalityIsKnowledgeNotArrivalOrder() {
		assertThat(NARROW.or(APART)).isEqualTo(APART.or(NARROW));
	}

	@Test
	public void andMeetsFactorsPointwise() {
		// [0,10] ∧ [3,6] = [3,6]: the conjunction is the domain intersection
		assertThat(WIDE.and(NARROW)).isEqualTo(NARROW);
	}

	@Test
	public void andDistributesOverOr() {
		Condition sum = NARROW.or(APART);

		assertThat(sum.and(WIDE))
				.isEqualTo(NARROW.and(WIDE).or(APART.and(WIDE)));
	}

	@Test
	public void oneIsTheProductIdentityAndZeroAnnihilates() {
		assertThat(NARROW.and(Condition.ONE)).isEqualTo(NARROW);
		assertThat(Condition.ONE.and(NARROW)).isEqualTo(NARROW);
		assertThat(NARROW.and(Condition.ZERO)).isEqualTo(Condition.ZERO);
	}
}
