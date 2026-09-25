package org.clauseway.logic.finitedomain.domains;

// ABOUTME: The one-value domain: the collapse point every narrowing aims at —
// ABOUTME: membership is equality, bounds are the value itself.

import org.clauseway.logic.finitedomain.Bound;
import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.vavr.control.Option;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Singleton<T> extends Domain<T> {
	T value;
	@EqualsAndHashCode.Exclude
	Comparator<T> order;
	@EqualsAndHashCode.Exclude
	Option<Discrete<T>> step;

	public static <T> Singleton<T> of(T value, Comparator<T> order, Option<Discrete<T>> step) {
		return new Singleton<>(value, order, step);
	}

	@Override
	public Domain<T> atLeast(Bound<T> bound) {
		return bound.asLowerAdmits(value, order) ? this : Empty.instance();
	}

	@Override
	public Domain<T> atMost(Bound<T> bound) {
		return bound.asUpperAdmits(value, order) ? this : Empty.instance();
	}

	@Override
	public Stream<T> stream() {
		return Stream.of(value);
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public Comparator<T> order() {
		return order;
	}

	@Override
	public Option<Discrete<T>> step() {
		return step;
	}

	@Override
	public Bound<T> lower() {
		return Bound.closed(value);
	}

	@Override
	public Bound<T> upper() {
		return Bound.closed(value);
	}

	@Override
	public boolean contains(T v) {
		return value.equals(v);
	}

	@Override
	public Domain<T> intersect(Domain<T> other) {
		return Option.of(value)
				.filter(other::contains)
				.<Domain<T>> map(v -> this)
				.getOrElse(Empty::instance);
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		if (other.asPoint().isDefined()) {
			return !(other.asPoint().get().equals(value));
		} else {
			return other.isDisjoint(this);
		}
	}

	@Override
	public <R> R accept(DomainVisitor<T, R> v) {
		return v.visit(this);
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		return other.contains(value) ?
				Empty.instance() : this;
	}

	@Override
	public String toString() {
		return "[" + value + "]";
	}

}
