package com.tgac.logic.fd;
import io.vavr.control.Option;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
public class SingletonFD extends FiniteDomain {
	long value;

	@Override
	public FiniteDomain dropBefore(Predicate<Long> p) {
		return p.test(value) ? this : new EmptyDomain();
	}
	@Override
	public FiniteDomain copyBefore(Predicate<Long> p) {
		return p.test(value) ? new EmptyDomain() : this;
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
	protected long min() {
		return value;
	}

	@Override
	protected long max() {
		return value;
	}

	@Override
	protected boolean contains(Object v) {
		return Long.valueOf(value).equals(v);
	}
	@Override
	protected FiniteDomain intersect(FiniteDomain other) {
		return Option.of(value)
				.filter(other::contains)
				.<FiniteDomain> map(v -> this)
				.getOrElse(EmptyDomain::new);
	}
	@Override
	protected Option<Long> getSingletonElement() {
		return Option.of(value);
	}
}
