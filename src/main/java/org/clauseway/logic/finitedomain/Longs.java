package org.clauseway.logic.finitedomain;

// ABOUTME: The Long front: every FD operation with Long's seats pre-specified —
// ABOUTME: natural order, exact arithmetic, unit stepping.

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.finitedomain.capabilities.Arithmetic;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.logic.finitedomain.capabilities.Multiplicative;
import org.clauseway.logic.finitedomain.domains.EnumeratedDomain;
import org.clauseway.logic.finitedomain.domains.Interval;
import org.clauseway.logic.finitedomain.domains.Singleton;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Longs {

	private static final Comparator<Long> ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<Long>> STEP = Option.of(Discrete.LONGS);

	public static Domain<Long> interval(long min, long max) {
		return Interval.of(min, max, ORDER, STEP);
	}

	public static Domain<Long> singleton(long value) {
		return Singleton.of(value, ORDER, STEP);
	}

	public static Domain<Long> range(long startInclusive, long endExclusive) {
		return EnumeratedDomain.of(Array.range(startInclusive, endExclusive), ORDER, STEP);
	}

	public static Domain<Long> enumerated(Long... values) {
		return EnumeratedDomain.of(Array.of(values), ORDER, STEP);
	}

	public static Posting leq(Unifiable<Long> less, Unifiable<Long> more) {
		return FiniteDomain.leq(less, more, ORDER);
	}

	public static Posting lss(Unifiable<Long> less, Unifiable<Long> more) {
		return FiniteDomain.lss(less, more, ORDER);
	}

	public static Posting gtr(Unifiable<Long> more, Unifiable<Long> less) {
		return FiniteDomain.gtr(more, less, ORDER);
	}

	public static Posting geq(Unifiable<Long> more, Unifiable<Long> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting addo(Unifiable<Long> a, Unifiable<Long> b, Unifiable<Long> c) {
		return FiniteDomain.addo(a, b, c, Arithmetic.LONGS, ORDER, STEP);
	}

	public static Posting subtracto(Unifiable<Long> a, Unifiable<Long> b, Unifiable<Long> c) {
		return FiniteDomain.subtracto(a, b, c, Arithmetic.LONGS, ORDER, STEP);
	}

	public static Posting multo(Unifiable<Long> a, Unifiable<Long> b, Unifiable<Long> c) {
		return FiniteDomain.multo(a, b, c, Multiplicative.LONGS, ORDER, STEP);
	}

	public static Posting divo(Unifiable<Long> divided, Unifiable<Long> divisor, Unifiable<Long> result) {
		return FiniteDomain.divo(divided, divisor, result, Multiplicative.LONGS, ORDER, STEP);
	}

	public static Posting separate(Unifiable<Long> l, Unifiable<Long> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
