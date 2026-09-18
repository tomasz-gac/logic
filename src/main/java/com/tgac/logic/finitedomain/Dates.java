package com.tgac.logic.finitedomain;

// ABOUTME: The LocalDate front: domains, comparisons and labelling by days —
// ABOUTME: date arithmetic is affine (date + day count) and waits for that slice.

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.Unifiable;
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
		return FiniteDomain.lss(less, more, ORDER, STEP);
	}

	public static Posting gtr(Unifiable<LocalDate> more, Unifiable<LocalDate> less) {
		return FiniteDomain.gtr(more, less, ORDER, STEP);
	}

	public static Posting geq(Unifiable<LocalDate> more, Unifiable<LocalDate> less) {
		return FiniteDomain.geq(more, less, ORDER);
	}

	public static Posting separate(Unifiable<LocalDate> l, Unifiable<LocalDate> r) {
		return FiniteDomain.separate(l, r, ORDER);
	}
}
