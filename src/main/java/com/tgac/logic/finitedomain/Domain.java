package com.tgac.logic.finitedomain;

// ABOUTME: The abstract domain over raw values: bounds, narrowing, set algebra —
// ABOUTME: each instance carries its order seat and, where the type has one, stepping.

import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.domains.DomainVisitor;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import io.vavr.control.Option;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;

/**
 * The prototype lattice instance (lattice.md §2): meet = intersect, ⊥ = the
 * wiped domain, entailment derived from the meet — the equal-domain
 * termination guard has been computing {@code leq} since before it had the
 * name. Laws pinned by AlgebraicLawCoverageTest across all subclasses.
 * The capability record ({@code lattice.Domain}) answers membership by
 * {@link #contains} and collapse by the {@link Singleton} case; stabilization
 * keeps the default exact equality — finite descent.
 *
 * <p>Values are raw {@code T}: a domain carries its {@link #order} seat (and
 * {@link #step} where the type is discrete) instead of wrapping every element.
 * The seats are excluded from equality — identity is the value set alone.
 */
@EqualsAndHashCode
public abstract class Domain<T> implements com.tgac.logic.lattice.Domain<Domain<T>> {

	@Override
	public Domain<T> meet(Domain<T> other) {
		return intersect(other);
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean admits(Object ground) {
		return contains((T) ground);
	}

	@Override
	public Option<Object> asPoint() {
		return this instanceof Singleton ?
				Option.of(((Singleton<T>) this).getValue()) :
				Option.none();
	}

	/**
	 * Containment without building the intersection: the bounds screen is an
	 * O(1) necessary condition, and for a contiguous {@code other} (an
	 * interval has no gaps) it is sufficient; only a gapped right-hand side
	 * (enumerated, union) walks this domain's values, early-exiting on the
	 * first escapee. The same order the meet derives — the laws sweeps check
	 * both against each other.
	 */
	@Override
	public boolean leq(Domain<T> other) {
		if (isEmpty()) {
			return true;
		}
		if (other.isEmpty()
				|| !Bound.coversLower(other.lower(), lower(), order())
				|| !Bound.coversUpper(other.upper(), upper(), order())) {
			return false;
		}
		if (other instanceof Interval) {
			return true;
		}
		return stream().allMatch(other::contains);
	}

	@Override
	public boolean isAbsorbing() {
		return isEmpty();
	}

	public abstract <R> R accept(DomainVisitor<T, R> v);

	public abstract boolean contains(T value);

	public abstract Stream<T> stream();

	public abstract boolean isEmpty();

	/** The order seat this domain's bounds and screens read through. */
	public abstract Comparator<T> order();

	/** The discreteness seat where the type has one — stepping, streaming, labelling. */
	public abstract Option<Discrete<T>> step();

	public abstract Bound<T> lower();

	public abstract Bound<T> upper();

	/** The bound values without their inclusivity — the arithmetic reading. */
	public final T min() {
		return lower().getValue();
	}

	public final T max() {
		return upper().getValue();
	}

	/**
	 * The values of this domain the bound admits from below. A type with a
	 * step seat normalizes an open bound to its closed form first — one
	 * spelling per value set, the identity the equal-domain guard relies on.
	 */
	public abstract Domain<T> atLeast(Bound<T> bound);

	/** The values of this domain the bound admits from above. */
	public abstract Domain<T> atMost(Bound<T> bound);

	public final Domain<T> atLeast(T value) {
		return atLeast(Bound.closed(value));
	}

	public final Domain<T> atMost(T value) {
		return atMost(Bound.closed(value));
	}

	public abstract Domain<T> intersect(Domain<T> other);

	public abstract boolean isDisjoint(Domain<T> other);

	public abstract Domain<T> difference(Domain<T> other);
}
