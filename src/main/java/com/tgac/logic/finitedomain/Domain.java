package com.tgac.logic.finitedomain;

import com.tgac.functional.algebra.Bottomed;
import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.DomainVisitor;
import com.tgac.logic.finitedomain.domains.Interval;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;

/**
 * The prototype lattice instance (lattice.md §2): meet = intersect, ⊥ = the
 * wiped domain, entailment derived from the meet — the equal-domain
 * termination guard has been computing {@code leq} since before it had the
 * name. Laws pinned by AlgebraicLawCoverageTest across all subclasses.
 */
@EqualsAndHashCode
public abstract class Domain<T> implements MeetSemilattice<Domain<T>>, Bottomed {

	@Override
	public Domain<T> meet(Domain<T> other) {
		return intersect(other);
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
				|| other.min().compareTo(min()) > 0
				|| other.max().compareTo(max()) < 0) {
			return false;
		}
		if (other instanceof Interval) {
			return true;
		}
		return stream().allMatch(other::contains);
	}

	@Override
	public boolean isBottom() {
		return isEmpty();
	}

	public abstract <R> R accept(DomainVisitor<T, R> v);

	public abstract boolean contains(T value);

	public abstract Stream<T> stream();

	public abstract boolean isEmpty();

	public abstract Arithmetic<T> min();

	public abstract Arithmetic<T> max();

	/**
	 * The values of this domain that are ≥ {@code value} (inclusive lower bound).
	 */
	public abstract Domain<T> atLeast(Arithmetic<T> value);

	/**
	 * The values of this domain that are ≤ {@code value} (inclusive upper bound).
	 */
	public abstract Domain<T> atMost(Arithmetic<T> value);

	public abstract Domain<T> intersect(Domain<T> other);

	public abstract boolean isDisjoint(Domain<T> other);

	public abstract Domain<T> difference(Domain<T> other);
}
