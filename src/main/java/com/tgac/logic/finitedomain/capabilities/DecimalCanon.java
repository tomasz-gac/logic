package com.tgac.logic.finitedomain.capabilities;

// ABOUTME: The decimal canonical form: one scale per numeric value — trailing
// ABOUTME: zeros stripped, integral values at scale zero (never 4E+1).

import java.math.BigDecimal;

final class DecimalCanon {

	private DecimalCanon() {
	}

	/** stripTrailingZeros alone maps 40 to 4E+1 (scale −1) — integral
	 * values re-anchor at scale zero so the representative is unique. */
	static BigDecimal canonical(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}
}
