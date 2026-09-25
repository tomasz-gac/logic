package org.clauseway.logic.finitedomain;

// ABOUTME: The Integer front: every FD operation with Integer's seats
// ABOUTME: pre-specified — natural order, exact arithmetic, unit stepping.

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.finitedomain.capabilities.Arithmetic;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.logic.finitedomain.capabilities.Multiplicative;
import org.clauseway.logic.finitedomain.domains.EnumeratedDomain;
import org.clauseway.logic.finitedomain.domains.Interval;
import org.clauseway.logic.finitedomain.domains.Singleton;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.vavr.collection.Array;
import org.clauseway.vavr.control.Option;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Ints {

	private static final Comparator<Integer> ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<Integer>> STEP = Option.of(Discrete.INTS);

	public static Domain<Integer> interval(int min, int max) {
		return Interval.of(min, max, ORDER, STEP);
	}

	public static Domain<Integer> singleton(int value) {
		return Singleton.of(value, ORDER, STEP);
	}

	public static Domain<Integer> range(int startInclusive, int endExclusive) {
		return EnumeratedDomain.of(Array.range(startInclusive, endExclusive), ORDER, STEP);
	}

	public static Domain<Integer> enumerated(Integer... values) {
		return EnumeratedDomain.of(Array.of(values), ORDER, STEP);
	}

	public static Posting leq(Unifiable<Integer> less, Unifiable<Integer> more) {
		return FiniteDomain.leq(less, more, ORDER);
	}

	public static Posting lss(Unifiable<Integer> less, Unifiable<Integer> more) {
		return FiniteDomain.lss(less, more, ORDER);
	}

	public static Posting gtr(Unifiable<Integer> more, Unifiable<Integer> less) {
		return FiniteDomain.gtr(more, less, ORDER);
	}

	public static Posting geq(Unifiable<Integer> more, Unifiable<Integer> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting addo(Unifiable<Integer> a, Unifiable<Integer> b, Unifiable<Integer> c) {
		return FiniteDomain.addo(a, b, c, Arithmetic.INTS, ORDER, STEP);
	}

	public static Posting subtracto(Unifiable<Integer> a, Unifiable<Integer> b, Unifiable<Integer> c) {
		return FiniteDomain.subtracto(a, b, c, Arithmetic.INTS, ORDER, STEP);
	}

	public static Posting multo(Unifiable<Integer> a, Unifiable<Integer> b, Unifiable<Integer> c) {
		return FiniteDomain.multo(a, b, c, Multiplicative.INTS, ORDER, STEP);
	}

	public static Posting divo(Unifiable<Integer> divided, Unifiable<Integer> divisor, Unifiable<Integer> result) {
		return FiniteDomain.divo(divided, divisor, result, Multiplicative.INTS, ORDER, STEP);
	}

	public static Posting separate(Unifiable<Integer> l, Unifiable<Integer> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
