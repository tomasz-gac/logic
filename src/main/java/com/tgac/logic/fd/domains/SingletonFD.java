package com.tgac.logic.fd.domains;
import com.tgac.logic.fd.FiniteDomain;
import io.vavr.control.Option;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
public class SingletonFD<T extends Comparable<T>> extends FiniteDomain<T> {
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
	public Stream<Object> stream() {
		return Stream.of(value);
	}
	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	protected T min() {
		return value;
	}

	@Override
	protected T max() {
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
	public String toString() {
		return "{" + value + "}";
	}
}
