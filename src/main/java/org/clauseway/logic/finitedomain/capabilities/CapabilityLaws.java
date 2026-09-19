package org.clauseway.logic.finitedomain.capabilities;

// ABOUTME: The admission receipts: executable laws every capability instance must
// ABOUTME: pass — clients run these against their own types before trusting them.

import java.util.Comparator;
import java.util.Objects;

/**
 * The laws of the seats, executable. A client bringing its own type
 * implements the interfaces and runs these checks over a SAMPLE
 * POPULATION — the values its program will actually contain; every
 * failure names the law and the witnesses. The laws are membership:
 * an instance that fails here is a bug in the instance (or a type
 * whose identity semantics need canonicalizing at construction),
 * never an acceptable flavor.
 *
 * <p>Samples are explicit, not generated: determinism is part of the
 * receipt, and only the client knows its type's treacherous corners
 * (scale twins, sign zeros, overflow edges — bring them).
 */
public final class CapabilityLaws {

	private CapabilityLaws() {
	}

	/**
	 * The three-way functional dependency, all readings: for every point
	 * {@code p} and delta {@code d}, with {@code c = plus(p, d)} —
	 * {@code minus(c, d) = p} and {@code between(p, c) = d}. Equality is
	 * {@code equals}: these values are identity carriers in the engine,
	 * so the laws must hold in the identity the engine uses.
	 */
	public static <P, V> void arithmetic(Arithmetic<P, V> arithmetic,
			Iterable<P> points, Iterable<V> deltas) {
		for (P p : points) {
			for (V d : deltas) {
				P c = arithmetic.plus(p, d);
				check(Objects.equals(arithmetic.minus(c, d), p),
						"minus undoes plus", p, d, c, arithmetic.minus(c, d));
				check(Objects.equals(arithmetic.between(p, c), d),
						"between recovers the delta", p, d, c, arithmetic.between(p, c));
			}
		}
	}

	/**
	 * The multiplicative inverse round trip, both directions, plus the
	 * identities: {@code times(x, b)} then {@code dividedExactly(·, b)}
	 * recovers {@code x} for every non-zero {@code b}; a some from
	 * {@code dividedExactly(a, b)} multiplies back to {@code a};
	 * {@code one} is neutral and {@code zero} absorbing. Divisors must
	 * exclude {@code zero()} — that is the door's own precondition.
	 */
	public static <T> void multiplicative(Multiplicative<T> multiplicative,
			Iterable<T> values, Iterable<T> nonZeroDivisors) {
		for (T b : nonZeroDivisors) {
			check(!Objects.equals(b, multiplicative.zero()),
					"divisor samples must exclude zero — the precondition is the caller's", b);
		}
		for (T x : values) {
			check(Objects.equals(multiplicative.times(x, multiplicative.one()), x),
					"one is neutral", x, multiplicative.times(x, multiplicative.one()));
			check(Objects.equals(multiplicative.times(x, multiplicative.zero()), multiplicative.zero()),
					"zero absorbs", x, multiplicative.times(x, multiplicative.zero()));
			for (T b : nonZeroDivisors) {
				T a = multiplicative.times(x, b);
				check(multiplicative.dividedExactly(a, b)
								.map(back -> Objects.equals(back, x))
								.getOrElse(false),
						"dividedExactly undoes times", x, b, a,
						multiplicative.dividedExactly(a, b));
				multiplicative.dividedExactly(x, b).forEach(quotient ->
						check(Objects.equals(multiplicative.times(quotient, b), x),
								"a some multiplies back", x, b, quotient));
			}
		}
	}

	/**
	 * Discreteness against the order seat: {@code prev(next(t)) = t},
	 * {@code next(prev(t)) = t}, and stepping moves strictly in order.
	 * (That {@code next(t)} is the LEAST value above {@code t} is the
	 * type author's obligation — no finite sample can witness it.)
	 */
	public static <T> void discrete(Discrete<T> discrete, Comparator<T> order,
			Iterable<T> values) {
		for (T t : values) {
			check(Objects.equals(discrete.prev(discrete.next(t)), t),
					"prev undoes next", t, discrete.next(t));
			check(Objects.equals(discrete.next(discrete.prev(t)), t),
					"next undoes prev", t, discrete.prev(t));
			check(order.compare(discrete.next(t), t) > 0,
					"next moves strictly up", t, discrete.next(t));
			check(order.compare(discrete.prev(t), t) < 0,
					"prev moves strictly down", t, discrete.prev(t));
		}
	}

	/**
	 * The identity-carrier laws — the receipt that admits a type into
	 * unification, tabling keys, and store images: {@code equals}
	 * consistent with {@code hashCode}, and comparison-equality implying
	 * object equality (CANONICAL FORM: a type with compare-equal but
	 * unequal twins — BigDecimal's {@code 0.5} vs {@code 0.50} —
	 * fails here, which is the kit telling you to canonicalize at
	 * construction or keep such twins out of your programs).
	 */
	public static <T> void identity(Comparator<T> order, Iterable<T> values) {
		for (T a : values) {
			for (T b : values) {
				if (Objects.equals(a, b)) {
					check(a.hashCode() == b.hashCode(),
							"equal values hash equally", a, b);
				}
				if (order.compare(a, b) == 0) {
					check(Objects.equals(a, b),
							"comparison-equal values are equal — canonical form", a, b);
				}
				check(Integer.signum(order.compare(a, b)) == -Integer.signum(order.compare(b, a)),
						"comparison is antisymmetric", a, b);
			}
		}
	}

	private static void check(boolean law, String name, Object... witnesses) {
		if (!law) {
			StringBuilder message = new StringBuilder("capability law broken: ").append(name);
			for (Object witness : witnesses) {
				message.append(" | ").append(witness);
			}
			throw new AssertionError(message.toString());
		}
	}
}
