package com.tgac.logic.fd.domains;
import io.vavr.control.Option;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
public class SingletonFD<T> extends FiniteDomain<T> {
	T value;

	@Override
	public FiniteDomain<T> dropBefore(Predicate<T> p) {
		return p.test(value) ? this : new EmptyDomain<>();
	}
	@Override
	public FiniteDomain<T> copyBefore(Predicate<T> p) {
		return p.test(value) ? new EmptyDomain<>() : this;
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
	public T min() {
		return value;
	}

	@Override
	public T max() {
		return value;
	}

	@Override
	public boolean contains(T v) {
		return value.equals(v);
	}
	@Override
	protected FiniteDomain<T> intersect(FiniteDomain<T> other) {
		return Option.of(value)
				.filter(other::contains)
				.<FiniteDomain<T>> map(v -> this)
				.getOrElse(EmptyDomain::new);
	}

	@Override
	protected Option<T> getSingletonElement() {
		return Option.of(value);
	}

	@Override
	public boolean isDisjoint(FiniteDomain<T> other) {
		return other.intersect(this).isEmpty();
	}
	@Override
	public FiniteDomain<T> difference(FiniteDomain<T> other) {
		return other.difference(this);
	}

	@Override
	public String toString() {
		return "[" + value + "]";
	}

}
