package com.tgac.logic.finitedomain;

// ABOUTME: The instance-explicit FD core: domain membership, order and arithmetic
// ABOUTME: schemas over any type that brings its capability seats.

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.finitedomain.relations.Add;
import com.tgac.logic.finitedomain.relations.Leq;
import com.tgac.logic.finitedomain.relations.Lss;
import com.tgac.logic.finitedomain.relations.Mul;
import com.tgac.logic.finitedomain.relations.Separate;
import com.tgac.logic.unification.Unifiable;
import io.vavr.control.Option;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The generic front door: every operation takes the capability seats it
 * needs as plain arguments — the typed fronts ({@link Ints}, {@link Longs},
 * {@link BigIntegers}, {@link BigDecimals}, {@link Dates}, {@link Instants})
 * pre-specify them so no ordinary caller ever states an instance. Strict
 * orders narrow through OPEN bounds ({@link Bound}); stepping plays no part
 * in comparison — a domain with a step seat normalizes the open bound to
 * its closed spelling itself.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FiniteDomain {

	/** The membership {@code u ∈ d} through the store's imposition door. */
	@SuppressWarnings("unchecked")
	public static <T> Posting dom(Unifiable<T> u, Domain<T> d) {
		return FiniteDomainConstraints.empty().impose(u, (Domain<Object>) d);
	}

	public static <T> Posting leq(Unifiable<T> less, Unifiable<T> more, Comparator<T> order) {
		return Propagation.activate(new Leq(less, more, order));
	}

	public static <T> Posting lss(Unifiable<T> less, Unifiable<T> more, Comparator<T> order) {
		return Propagation.activate(new Lss(less, more, order));
	}

	public static <T> Posting gtr(Unifiable<T> more, Unifiable<T> less, Comparator<T> order) {
		// more > less IS less < more: one sharp atom, the schema's own doom
		return Propagation.activate(new Lss(less, more, order));
	}

	public static <T> Posting geq(Unifiable<T> more, Unifiable<T> less, Comparator<T> order) {
		// more >= less violated ⟺ less <= more violated: the schema's own doom
		return Propagation.activate(new Leq(less, more, order));
	}

	public static <T> Posting addo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c,
			Arithmetic<T, T> arithmetic, Comparator<T> order, Option<Discrete<T>> step) {
		return Propagation.activate(new Add(a, b, c, arithmetic, order, step));
	}

	public static <T> Posting subtracto(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c,
			Arithmetic<T, T> arithmetic, Comparator<T> order, Option<Discrete<T>> step) {
		return addo(c, b, a, arithmetic, order, step);
	}

	public static <T> Posting multo(Unifiable<T> a, Unifiable<T> b, Unifiable<T> c,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		return Propagation.activate(new Mul(a, b, c, multiplicative, order, step));
	}

	public static <T> Posting divo(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> result,
			Multiplicative<T> multiplicative, Comparator<T> order, Option<Discrete<T>> step) {
		return multo(result, divisor, divided, multiplicative, order, step);
	}

	public static <T> Posting separate(Unifiable<T> l, Unifiable<T> r, Comparator<T> order) {
		return Propagation.activate(new Separate(l, r, order));
	}
}
