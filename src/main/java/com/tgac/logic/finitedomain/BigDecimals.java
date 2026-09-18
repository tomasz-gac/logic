package com.tgac.logic.finitedomain;

// ABOUTME: The exact-decimal front: order, affine and multiplicative seats with
// ABOUTME: canonical emissions — dense, so intervals propagate and never label.

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.math.BigDecimal;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * BigDecimal has no {@code Discrete} seat: an {@link #interval} domain
 * narrows and decides but refuses labelling loudly; an {@link #enumerated}
 * domain knows its elements and labels without stepping. Values should
 * arrive canonical (one scale per numeric value — computed results are);
 * {@code CapabilityLaws.identity} is the receipt that a population is.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigDecimals {

	private static final Comparator<BigDecimal> ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<BigDecimal>> STEP = Option.none();

	public static Domain<BigDecimal> interval(BigDecimal min, BigDecimal max) {
		return Interval.of(min, max, ORDER, STEP);
	}

	public static Domain<BigDecimal> singleton(BigDecimal value) {
		return Singleton.of(value, ORDER, STEP);
	}

	public static Domain<BigDecimal> enumerated(BigDecimal... values) {
		return EnumeratedDomain.of(Array.of(values), ORDER, STEP);
	}

	public static Posting leq(Unifiable<BigDecimal> less, Unifiable<BigDecimal> more) {
		return FiniteDomain.leq(less, more, ORDER);
	}

	public static Posting lss(Unifiable<BigDecimal> less, Unifiable<BigDecimal> more) {
		return FiniteDomain.lss(less, more, ORDER);
	}

	public static Posting gtr(Unifiable<BigDecimal> more, Unifiable<BigDecimal> less) {
		return FiniteDomain.gtr(more, less, ORDER);
	}

	public static Posting geq(Unifiable<BigDecimal> more, Unifiable<BigDecimal> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting addo(Unifiable<BigDecimal> a, Unifiable<BigDecimal> b, Unifiable<BigDecimal> c) {
		return FiniteDomain.addo(a, b, c, Arithmetic.BIG_DECIMALS, ORDER, STEP);
	}

	public static Posting subtracto(Unifiable<BigDecimal> a, Unifiable<BigDecimal> b, Unifiable<BigDecimal> c) {
		return FiniteDomain.subtracto(a, b, c, Arithmetic.BIG_DECIMALS, ORDER, STEP);
	}

	public static Posting multo(Unifiable<BigDecimal> a, Unifiable<BigDecimal> b, Unifiable<BigDecimal> c) {
		return FiniteDomain.multo(a, b, c, Multiplicative.BIG_DECIMALS, ORDER, STEP);
	}

	public static Posting divo(Unifiable<BigDecimal> divided, Unifiable<BigDecimal> divisor, Unifiable<BigDecimal> result) {
		return FiniteDomain.divo(divided, divisor, result, Multiplicative.BIG_DECIMALS, ORDER, STEP);
	}

	public static Posting separate(Unifiable<BigDecimal> l, Unifiable<BigDecimal> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
