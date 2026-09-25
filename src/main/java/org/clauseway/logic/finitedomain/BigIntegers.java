package org.clauseway.logic.finitedomain;

// ABOUTME: The BigInteger front: every FD operation with BigInteger's seats
// ABOUTME: pre-specified — natural order, unbounded exact arithmetic, unit stepping.

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.finitedomain.capabilities.Arithmetic;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.logic.finitedomain.capabilities.Multiplicative;
import org.clauseway.logic.finitedomain.domains.EnumeratedDomain;
import org.clauseway.logic.finitedomain.domains.Interval;
import org.clauseway.logic.finitedomain.domains.Singleton;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.vavr.collection.Array;
import org.clauseway.vavr.collection.Iterator;
import org.clauseway.vavr.control.Option;
import java.math.BigInteger;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigIntegers {

	private static final Comparator<BigInteger> ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<BigInteger>> STEP = Option.of(Discrete.BIG_INTEGERS);

	public static Domain<BigInteger> interval(BigInteger min, BigInteger max) {
		return Interval.of(min, max, ORDER, STEP);
	}

	public static Domain<BigInteger> singleton(BigInteger value) {
		return Singleton.of(value, ORDER, STEP);
	}

	public static Domain<BigInteger> range(BigInteger startInclusive, BigInteger endExclusive) {
		return EnumeratedDomain.of(
				Iterator.iterate(startInclusive, i -> i.add(BigInteger.ONE))
						.takeWhile(v -> v.compareTo(endExclusive) < 0)
						.collect(Array.collector()),
				ORDER, STEP);
	}

	public static Domain<BigInteger> enumerated(BigInteger... values) {
		return EnumeratedDomain.of(Array.of(values), ORDER, STEP);
	}

	public static Posting leq(Unifiable<BigInteger> less, Unifiable<BigInteger> more) {
		return FiniteDomain.leq(less, more, ORDER);
	}

	public static Posting lss(Unifiable<BigInteger> less, Unifiable<BigInteger> more) {
		return FiniteDomain.lss(less, more, ORDER);
	}

	public static Posting gtr(Unifiable<BigInteger> more, Unifiable<BigInteger> less) {
		return FiniteDomain.gtr(more, less, ORDER);
	}

	public static Posting geq(Unifiable<BigInteger> more, Unifiable<BigInteger> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting addo(Unifiable<BigInteger> a, Unifiable<BigInteger> b, Unifiable<BigInteger> c) {
		return FiniteDomain.addo(a, b, c, Arithmetic.BIG_INTEGERS, ORDER, STEP);
	}

	public static Posting subtracto(Unifiable<BigInteger> a, Unifiable<BigInteger> b, Unifiable<BigInteger> c) {
		return FiniteDomain.subtracto(a, b, c, Arithmetic.BIG_INTEGERS, ORDER, STEP);
	}

	public static Posting multo(Unifiable<BigInteger> a, Unifiable<BigInteger> b, Unifiable<BigInteger> c) {
		return FiniteDomain.multo(a, b, c, Multiplicative.BIG_INTEGERS, ORDER, STEP);
	}

	public static Posting divo(Unifiable<BigInteger> divided, Unifiable<BigInteger> divisor, Unifiable<BigInteger> result) {
		return FiniteDomain.divo(divided, divisor, result, Multiplicative.BIG_INTEGERS, ORDER, STEP);
	}

	public static Posting separate(Unifiable<BigInteger> l, Unifiable<BigInteger> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
