package com.tgac.logic.fd.domains;
import com.tgac.logic.fd.FiniteDomain;
import io.vavr.control.Option;

import java.util.function.Predicate;
import java.util.stream.Stream;
public class EmptyDomain<T extends Comparable<T>> extends FiniteDomain<T> {
	@Override
	public FiniteDomain<T> dropBefore(Predicate<T> p) {
		return this;
	}
	@Override
	public FiniteDomain<T> copyBefore(Predicate<T> p) {
		return this;
	}
	@Override
	public Stream<Object> stream() {
		return Stream.empty();
	}
	@Override
	public boolean isEmpty() {
		return true;
	}
	@Override
	protected T min() {
		throw new UnsupportedOperationException("Cannot call min on empty domain");
	}
	@Override
	protected T max() {
		throw new UnsupportedOperationException("Cannot call max on empty domain");
	}
	@Override
	public boolean contains(T v) {
		return false;
	}
	@Override
	protected FiniteDomain<T> intersect(FiniteDomain<T> other) {
		return this;
	}
	@Override
	protected Option<T> getSingletonElement() {
		return Option.none();
	}

	@Override
	public String toString() {
		return "{}";
	}
}
