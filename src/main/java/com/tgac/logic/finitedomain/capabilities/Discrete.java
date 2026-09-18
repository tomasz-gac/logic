package com.tgac.logic.finitedomain.capabilities;

// ABOUTME: The discreteness seat: successor and predecessor — what the labelling
// ABOUTME: floor steps by; a dense type simply lacks the instance, loudly.

import java.math.BigInteger;
import java.time.LocalDate;

/**
 * The discreteness capability: every value has an immediate successor
 * and predecessor, so an interval can be STEPPED — enumerated at
 * labelling, streamed at the ground floor. This is its own seat, not a
 * corollary of arithmetic: the old fused shape derived {@code next} as
 * {@code add(unit)}, which forced every arithmetic type to pretend a
 * unit exists. A dense type ({@code BigDecimal}) holds order and
 * arithmetic, keeps interval domains and propagation, and simply LACKS
 * this instance — the ground floor's refusal to label it becomes a
 * typed absence, not a runtime surprise.
 *
 * <p>Contract: {@code prev(next(t)) = t} and {@code next} agrees with
 * the order seat — {@code next(t)} is the least value strictly above
 * {@code t}. Operation-only and stateless, same replay contract as
 * every seat.
 */
public interface Discrete<T> {

	T next(T value);

	T prev(T value);

	Discrete<Integer> INTS = new Discrete<Integer>() {
		@Override
		public Integer next(Integer value) {
			return value + 1;
		}

		@Override
		public Integer prev(Integer value) {
			return value - 1;
		}
	};

	Discrete<Long> LONGS = new Discrete<Long>() {
		@Override
		public Long next(Long value) {
			return value + 1L;
		}

		@Override
		public Long prev(Long value) {
			return value - 1L;
		}
	};

	Discrete<BigInteger> BIG_INTEGERS = new Discrete<BigInteger>() {
		@Override
		public BigInteger next(BigInteger value) {
			return value.add(BigInteger.ONE);
		}

		@Override
		public BigInteger prev(BigInteger value) {
			return value.subtract(BigInteger.ONE);
		}
	};

	/**
	 * Dates step by days — genuinely discrete, labellable. Float, Double,
	 * BigDecimal, and Instant have NO instance here deliberately: dense
	 * (or absurdly fine) types keep order, arithmetic, and interval
	 * domains, and the labelling floor refuses them by this absence.
	 */
	Discrete<LocalDate> DATES = new Discrete<LocalDate>() {
		@Override
		public LocalDate next(LocalDate value) {
			return value.plusDays(1);
		}

		@Override
		public LocalDate prev(LocalDate value) {
			return value.minusDays(1);
		}
	};
}
