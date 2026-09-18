package com.tgac.logic.finitedomain;

// ABOUTME: The Instant front: domains, comparisons and the affine arithmetic —
// ABOUTME: an instant shifted by a duration; nano-fine, so no labelling.

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Instants {

	private static final Comparator<Instant> ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<Instant>> STEP = Option.none();
	private static final Comparator<Duration> DURATION_ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<Duration>> DURATION_STEP = Option.none();

	public static Domain<Instant> interval(Instant min, Instant max) {
		return Interval.of(min, max, ORDER, STEP);
	}

	public static Domain<Instant> singleton(Instant value) {
		return Singleton.of(value, ORDER, STEP);
	}

	public static Domain<Instant> enumerated(Instant... values) {
		return EnumeratedDomain.of(Array.of(values), ORDER, STEP);
	}

	public static Posting leq(Unifiable<Instant> less, Unifiable<Instant> more) {
		return FiniteDomain.leq(less, more, ORDER);
	}

	public static Posting lss(Unifiable<Instant> less, Unifiable<Instant> more) {
		return FiniteDomain.lss(less, more, ORDER);
	}

	public static Posting gtr(Unifiable<Instant> more, Unifiable<Instant> less) {
		return FiniteDomain.gtr(more, less, ORDER);
	}

	public static Posting geq(Unifiable<Instant> more, Unifiable<Instant> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting addo(Unifiable<Instant> instant, Unifiable<Duration> delta, Unifiable<Instant> shifted) {
		return FiniteDomain.addo(instant, delta, shifted, Arithmetic.INSTANTS,
				ORDER, STEP, DURATION_ORDER, DURATION_STEP);
	}

	public static Posting subtracto(Unifiable<Instant> instant, Unifiable<Duration> delta, Unifiable<Instant> result) {
		return FiniteDomain.subtracto(instant, delta, result, Arithmetic.INSTANTS,
				ORDER, STEP, DURATION_ORDER, DURATION_STEP);
	}

	public static Posting separate(Unifiable<Instant> l, Unifiable<Instant> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
