package com.tgac.logic.finitedomain;


import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.DomainVisitor;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public abstract class Domain<T> {

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
