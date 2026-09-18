package com.tgac.logic.finitedomain;

// ABOUTME: One interval endpoint: a value with its inclusivity. The whole
// ABOUTME: open/closed endpoint algebra lives here — tie-breaks, viability, touch.

import java.util.Comparator;
import lombok.Value;

/**
 * An endpoint is a value AND whether the value itself belongs: {@code [5}
 * versus {@code (5}. Strictness stops being encoded by stepping — a dense
 * type narrows {@code x < b} to an OPEN upper bound instead of faking the
 * nearest closed one — and every endpoint rule (which bound is tighter,
 * when two bounds leave room between them, when two intervals touch) is a
 * question about two bounds, answered once, here. A bound does not know
 * which side it stands on; the side-sensitive readings carry it in the
 * method name.
 *
 * <p>Pure data: value plus flag, no comparator inside — every reading
 * takes the order seat as an argument, like the domains that carry it.
 */
@Value
public class Bound<T> {
	T value;
	boolean included;

	public static <T> Bound<T> closed(T value) {
		return new Bound<>(value, true);
	}

	public static <T> Bound<T> open(T value) {
		return new Bound<>(value, false);
	}

	/** The same value with the point excluded — how strict orders narrow. */
	public Bound<T> opened() {
		return included ? new Bound<>(value, false) : this;
	}

	/** The other side of a cut at this endpoint — how difference trims. */
	public Bound<T> complement() {
		return new Bound<>(value, !included);
	}

	/** Read as a LOWER bound: does {@code v} lie at-or-above it? */
	public boolean asLowerAdmits(T v, Comparator<T> order) {
		int c = order.compare(value, v);
		return c < 0 || (c == 0 && included);
	}

	/** Read as an UPPER bound: does {@code v} lie at-or-below it? */
	public boolean asUpperAdmits(T v, Comparator<T> order) {
		int c = order.compare(value, v);
		return c > 0 || (c == 0 && included);
	}

	/** The tighter of two lower bounds: higher value wins; at a tie, open. */
	public static <T> Bound<T> tighterLower(Bound<T> a, Bound<T> b, Comparator<T> order) {
		int c = order.compare(a.value, b.value);
		if (c != 0) {
			return c > 0 ? a : b;
		}
		return a.included ? b : a;
	}

	/** The tighter of two upper bounds: lower value wins; at a tie, open. */
	public static <T> Bound<T> tighterUpper(Bound<T> a, Bound<T> b, Comparator<T> order) {
		int c = order.compare(a.value, b.value);
		if (c != 0) {
			return c < 0 ? a : b;
		}
		return a.included ? b : a;
	}

	/** The looser of two upper bounds: higher value wins; at a tie, closed. */
	public static <T> Bound<T> looserUpper(Bound<T> a, Bound<T> b, Comparator<T> order) {
		int c = order.compare(a.value, b.value);
		if (c != 0) {
			return c > 0 ? a : b;
		}
		return a.included ? a : b;
	}

	/** Is there any value between the bounds — {@code (5, 5]} has none, {@code [5, 5]} has one. */
	public static <T> boolean viable(Bound<T> lower, Bound<T> upper, Comparator<T> order) {
		int c = order.compare(lower.value, upper.value);
		return c < 0 || (c == 0 && lower.included && upper.included);
	}

	/** Exactly one value between the bounds: both closed on the same value. */
	public static <T> boolean point(Bound<T> lower, Bound<T> upper, Comparator<T> order) {
		return order.compare(lower.value, upper.value) == 0 && lower.included && upper.included;
	}

	/**
	 * Does an interval ending at {@code upper} SHARE A POINT with one
	 * starting at {@code lower}? At equal values only if both include it —
	 * {@code [0,1)} and {@code [1,2]} are disjoint sets.
	 */
	public static <T> boolean overlaps(Bound<T> upper, Bound<T> lower, Comparator<T> order) {
		int c = order.compare(upper.value, lower.value);
		return c > 0 || (c == 0 && upper.included && lower.included);
	}

	/**
	 * Is an interval ending at {@code upper} CONTIGUOUS with one starting
	 * at {@code lower} — overlapping or seamlessly touching? At equal
	 * values one included side suffices: {@code …1)[1…} closes the seam,
	 * {@code …1)(1…} leaves a hole at the point.
	 */
	public static <T> boolean meets(Bound<T> upper, Bound<T> lower, Comparator<T> order) {
		int c = order.compare(upper.value, lower.value);
		return c > 0 || (c == 0 && (upper.included || lower.included));
	}

	/** Does {@code outer} (as a lower bound) admit everything {@code inner} admits? */
	public static <T> boolean coversLower(Bound<T> outer, Bound<T> inner, Comparator<T> order) {
		int c = order.compare(outer.value, inner.value);
		return c < 0 || (c == 0 && (outer.included || !inner.included));
	}

	/** Does {@code outer} (as an upper bound) admit everything {@code inner} admits? */
	public static <T> boolean coversUpper(Bound<T> outer, Bound<T> inner, Comparator<T> order) {
		int c = order.compare(outer.value, inner.value);
		return c > 0 || (c == 0 && (outer.included || !inner.included));
	}
}
