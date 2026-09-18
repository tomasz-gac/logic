package com.tgac.logic.finitedomain.capabilities;

// ABOUTME: The multiplicative seat, homogeneous: times and the exact-or-refuse
// ABOUTME: inverse — division is a decision, never an approximation.

import io.vavr.control.Option;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The multiplicative capability — homogeneous, and honestly PARTIAL on
 * the way back: {@code dividedExactly(a, b)} answers the {@code x} with
 * {@code times(x, b) = a} when exactly one exists, and {@code none}
 * otherwise. None is KNOWLEDGE, not a shrug — for integers,
 * {@code 7 / 2} refutes the triple {@code mul(x, 2, 7)} outright (no
 * integer exists), and a constraint reads it as failure, never as
 * something to approximate. Division by zero is the same {@code none}
 * (times(x, 0) = a has no unique x).
 *
 * <p>{@link #zero} and {@link #one} anchor the sign analysis interval
 * multiplication needs (the mulIntervals sign-guard) and the trivial
 * cases. Operation-only and stateless, same replay contract as every
 * seat.
 */
public interface Multiplicative<T> {

	T times(T a, T b);

	/**
	 * The exact inverse: the unique {@code x} with {@code times(x, b) = a},
	 * or none — and none is a REFUTATION. PRECONDITION: {@code b} is not
	 * {@link #zero()} — the zero cases split semantically (a ≠ 0 refutes,
	 * a = 0 is underdetermined: every x satisfies it) and only the CALLER
	 * can tell them apart, checking against {@code zero()} first.
	 */
	Option<T> dividedExactly(T a, T b);

	T zero();

	T one();

	Multiplicative<Integer> INTS = new Multiplicative<Integer>() {
		@Override
		public Integer times(Integer a, Integer b) {
			return Math.multiplyExact(a, b);
		}

		@Override
		public Option<Integer> dividedExactly(Integer a, Integer b) {
			return b == 0 || a % b != 0 ? Option.none() : Option.of(a / b);
		}

		@Override
		public Integer zero() {
			return 0;
		}

		@Override
		public Integer one() {
			return 1;
		}
	};

	Multiplicative<Long> LONGS = new Multiplicative<Long>() {
		@Override
		public Long times(Long a, Long b) {
			return Math.multiplyExact(a, b);
		}

		@Override
		public Option<Long> dividedExactly(Long a, Long b) {
			return b == 0L || a % b != 0L ? Option.none() : Option.of(a / b);
		}

		@Override
		public Long zero() {
			return 0L;
		}

		@Override
		public Long one() {
			return 1L;
		}
	};

	Multiplicative<BigInteger> BIG_INTEGERS = new Multiplicative<BigInteger>() {
		@Override
		public BigInteger times(BigInteger a, BigInteger b) {
			return a.multiply(b);
		}

		@Override
		public Option<BigInteger> dividedExactly(BigInteger a, BigInteger b) {
			if (b.signum() == 0) {
				return Option.none();
			}
			BigInteger[] quotientAndRemainder = a.divideAndRemainder(b);
			return quotientAndRemainder[1].signum() == 0
					? Option.of(quotientAndRemainder[0])
					: Option.none();
		}

		@Override
		public BigInteger zero() {
			return BigInteger.ZERO;
		}

		@Override
		public BigInteger one() {
			return BigInteger.ONE;
		}
	};

	/**
	 * The exact-or-refuse ruling in its purest form: a non-terminating
	 * quotient (1/3) IS the none — no scale policy, no rounding mode,
	 * division stays a decision.
	 */
	Multiplicative<BigDecimal> BIG_DECIMALS = new Multiplicative<BigDecimal>() {
		@Override
		public BigDecimal times(BigDecimal a, BigDecimal b) {
			return DecimalCanon.canonical(a.multiply(b));
		}

		@Override
		public Option<BigDecimal> dividedExactly(BigDecimal a, BigDecimal b) {
			if (b.signum() == 0) {
				return Option.none();
			}
			try {
				return Option.of(DecimalCanon.canonical(a.divide(b)));
			} catch (ArithmeticException nonTerminating) {
				return Option.none();
			}
		}

		@Override
		public BigDecimal zero() {
			return BigDecimal.ZERO;
		}

		@Override
		public BigDecimal one() {
			return BigDecimal.ONE;
		}
	};
}
