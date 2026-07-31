package com.tgac.logic.tabling;

// ABOUTME: Residues' contract: ⊗ is pointwise factor meet with TRUE as identity,
// ABOUTME: leq is containment (narrower entails wider), absorption flips with meet.

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ResiduesTest {

	private static final Residues WIDE = Span.factor(0L, 10L);
	private static final Residues NARROW = Span.factor(3L, 6L);
	private static final Residues APART = Span.factor(20L, 30L);

	@Test
	public void meetIntersectsSharedFactors() {
		// [0,10] ∧ [3,6] = [3,6]: the conjunction is the interval intersection
		assertThat(WIDE.meet(NARROW)).isEqualTo(NARROW);
		assertThat(NARROW.meet(WIDE)).isEqualTo(NARROW);
	}

	@Test
	public void trueIsTheMeetIdentity() {
		// an absent factor is that store's ⊤ - meeting it in changes nothing
		assertThat(Residues.TRUE.meet(NARROW)).isEqualTo(NARROW);
		assertThat(NARROW.meet(Residues.TRUE)).isEqualTo(NARROW);
		assertThat(Residues.TRUE.isTrue()).isTrue();
		assertThat(NARROW.isTrue()).isFalse();
	}

	@Test
	public void leqIsContainment() {
		// narrower entails wider; everything entails TRUE; TRUE entails nothing below it
		assertThat(NARROW.leq(WIDE)).isTrue();
		assertThat(WIDE.leq(NARROW)).isFalse();
		assertThat(NARROW.leq(Residues.TRUE)).isTrue();
		assertThat(Residues.TRUE.leq(NARROW)).isFalse();
		assertThat(NARROW.leq(APART)).isFalse();
		assertThat(APART.leq(NARROW)).isFalse();
	}

	@Test
	public void absorptionFlipsWithMeet() {
		// combine = meet accumulates DOWNWARD: the wider conjunct is the one
		// that contributes nothing - absorbedBy(other) = other.leq(this)
		assertThat(WIDE.absorbedBy(NARROW)).isTrue();
		assertThat(NARROW.absorbedBy(WIDE)).isFalse();
		assertThat(Residues.TRUE.absorbedBy(NARROW)).isTrue();
	}

	@Test
	public void equalityIsTheFactors() {
		assertThat(Span.factor(3L, 6L)).isEqualTo(NARROW);
		assertThat(WIDE.meet(NARROW)).isEqualTo(Span.factor(3L, 6L));
		assertThat(WIDE).isNotEqualTo(NARROW);
	}
}
