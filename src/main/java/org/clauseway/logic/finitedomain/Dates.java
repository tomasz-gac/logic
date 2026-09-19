package org.clauseway.logic.finitedomain;

// ABOUTME: The LocalDate front: domains, comparisons, labelling by days, and the
// ABOUTME: affine arithmetic — a date shifted by a day count.

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.finitedomain.capabilities.Arithmetic;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.logic.finitedomain.domains.EnumeratedDomain;
import org.clauseway.logic.finitedomain.domains.Interval;
import org.clauseway.logic.finitedomain.domains.Singleton;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.time.LocalDate;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Dates {

	private static final Comparator<LocalDate> ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<LocalDate>> STEP = Option.of(Discrete.DATES);
	private static final Comparator<Long> DAY_COUNT_ORDER = Comparator.naturalOrder();
	private static final Option<Discrete<Long>> DAY_COUNT_STEP = Option.of(Discrete.LONGS);

	public static Domain<LocalDate> interval(LocalDate min, LocalDate max) {
		return Interval.of(min, max, ORDER, STEP);
	}

	public static Domain<LocalDate> singleton(LocalDate value) {
		return Singleton.of(value, ORDER, STEP);
	}

	public static Domain<LocalDate> enumerated(LocalDate... values) {
		return EnumeratedDomain.of(Array.of(values), ORDER, STEP);
	}

	public static Posting leq(Unifiable<LocalDate> less, Unifiable<LocalDate> more) {
		return FiniteDomain.leq(less, more, ORDER);
	}

	public static Posting lss(Unifiable<LocalDate> less, Unifiable<LocalDate> more) {
		return FiniteDomain.lss(less, more, ORDER);
	}

	public static Posting gtr(Unifiable<LocalDate> more, Unifiable<LocalDate> less) {
		return FiniteDomain.gtr(more, less, ORDER);
	}

	public static Posting geq(Unifiable<LocalDate> more, Unifiable<LocalDate> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting addo(Unifiable<LocalDate> day, Unifiable<Long> days, Unifiable<LocalDate> shifted) {
		return FiniteDomain.addo(day, days, shifted, Arithmetic.DATES,
				ORDER, STEP, DAY_COUNT_ORDER, DAY_COUNT_STEP);
	}

	public static Posting subtracto(Unifiable<LocalDate> day, Unifiable<Long> days, Unifiable<LocalDate> result) {
		return FiniteDomain.subtracto(day, days, result, Arithmetic.DATES,
				ORDER, STEP, DAY_COUNT_ORDER, DAY_COUNT_STEP);
	}

	public static Posting separate(Unifiable<LocalDate> l, Unifiable<LocalDate> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
