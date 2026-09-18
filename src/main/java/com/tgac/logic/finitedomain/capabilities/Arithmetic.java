package com.tgac.logic.finitedomain.capabilities;

// ABOUTME: The additive seat, affine: points and deltas — plus, its inverse, and
// ABOUTME: the difference between two points; operation-only, stateless, no value.

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The additive capability, in its honest AFFINE shape: a point type
 * {@code P} acted on by a delta type {@code V}. {@code Instant} plus
 * {@code Duration} is an {@code Instant}; two {@code Instant}s differ
 * by a {@code Duration}; no {@code Instant} is ever added to another.
 * Numbers are the degenerate case {@code P = V}.
 *
 * <p>The three operations are one functional dependency read three
 * ways — {@code plus(a, d) = c} iff {@code minus(c, d) = a} iff
 * {@code between(a, c) = d} — which is exactly what lets a constraint
 * over the triple compute any position from the other two.
 *
 * <p>Operation-only: an instance wraps NO value and holds NO state —
 * propagators carry instances across replay rebuilds, so this is a
 * contract, not a style preference.
 */
public interface Arithmetic<P, V> {

	/** The point moved by the delta: {@code point + delta}. */
	P plus(P point, V delta);

	/** The move undone: {@code point - delta}; inverse of {@link #plus}. */
	P minus(P point, V delta);

	/** The delta between two points: {@code to - from}. */
	V between(P from, P to);

	/** Overflow THROWS — a constraint engine must never bind a wrapped lie. */
	Arithmetic<Integer, Integer> INTS = new Arithmetic<Integer, Integer>() {
		@Override
		public Integer plus(Integer point, Integer delta) {
			return Math.addExact(point, delta);
		}

		@Override
		public Integer minus(Integer point, Integer delta) {
			return Math.subtractExact(point, delta);
		}

		@Override
		public Integer between(Integer from, Integer to) {
			return Math.subtractExact(to, from);
		}
	};

	/** Overflow THROWS — a constraint engine must never bind a wrapped lie. */
	Arithmetic<Long, Long> LONGS = new Arithmetic<Long, Long>() {
		@Override
		public Long plus(Long point, Long delta) {
			return Math.addExact(point, delta);
		}

		@Override
		public Long minus(Long point, Long delta) {
			return Math.subtractExact(point, delta);
		}

		@Override
		public Long between(Long from, Long to) {
			return Math.subtractExact(to, from);
		}
	};

	Arithmetic<BigInteger, BigInteger> BIG_INTEGERS = new Arithmetic<BigInteger, BigInteger>() {
		@Override
		public BigInteger plus(BigInteger point, BigInteger delta) {
			return point.add(delta);
		}

		@Override
		public BigInteger minus(BigInteger point, BigInteger delta) {
			return point.subtract(delta);
		}

		@Override
		public BigInteger between(BigInteger from, BigInteger to) {
			return to.subtract(from);
		}
	};

	/**
	 * Exact decimal arithmetic, emissions CANONICAL: every result is
	 * stripped of trailing zeros, so computed values carry one scale per
	 * numeric value — system-minted identity is predictable; stated
	 * values remain the caller's own scales, by convention.
	 */
	Arithmetic<BigDecimal, BigDecimal> BIG_DECIMALS = new Arithmetic<BigDecimal, BigDecimal>() {
		@Override
		public BigDecimal plus(BigDecimal point, BigDecimal delta) {
			return DecimalCanon.canonical(point.add(delta));
		}

		@Override
		public BigDecimal minus(BigDecimal point, BigDecimal delta) {
			return DecimalCanon.canonical(point.subtract(delta));
		}

		@Override
		public BigDecimal between(BigDecimal from, BigDecimal to) {
			return DecimalCanon.canonical(to.subtract(from));
		}
	};

	/**
	 * Dates move by DAY COUNTS, not {@link java.time.Period}: a month is
	 * not an exact delta (Jan 31 + 1 month − 1 month = Jan 28), and the
	 * three-way dependency demands exact inverses. The first genuinely
	 * affine instance: P and V are different types.
	 */
	Arithmetic<LocalDate, Long> DATES = new Arithmetic<LocalDate, Long>() {
		@Override
		public LocalDate plus(LocalDate point, Long days) {
			return point.plusDays(days);
		}

		@Override
		public LocalDate minus(LocalDate point, Long days) {
			return point.minusDays(days);
		}

		@Override
		public Long between(LocalDate from, LocalDate to) {
			return ChronoUnit.DAYS.between(from, to);
		}
	};

	/** Instants move by {@link Duration} — exact to the nano, honestly affine. */
	Arithmetic<Instant, Duration> INSTANTS = new Arithmetic<Instant, Duration>() {
		@Override
		public Instant plus(Instant point, Duration delta) {
			return point.plus(delta);
		}

		@Override
		public Instant minus(Instant point, Duration delta) {
			return point.minus(delta);
		}

		@Override
		public Duration between(Instant from, Instant to) {
			return Duration.between(from, to);
		}
	};
}
