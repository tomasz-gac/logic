package com.tgac.logic.fd;
import io.vavr.control.Option;

import java.util.function.Predicate;
import java.util.stream.Stream;
public class EmptyDomain extends FiniteDomain {
	@Override
	public FiniteDomain dropBefore(Predicate<Long> p) {
		return this;
	}
	@Override
	public FiniteDomain copyBefore(Predicate<Long> p) {
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
	protected long min() {
		throw new UnsupportedOperationException("Cannot call min on empty domain");
	}
	@Override
	protected long max() {
		throw new UnsupportedOperationException("Cannot call max on empty domain");
	}
	@Override
	protected boolean contains(Object v) {
		return false;
	}
	@Override
	protected FiniteDomain intersect(FiniteDomain other) {
		return this;
	}
	@Override
	protected Option<Long> getSingletonElement() {
		return Option.none();
	}
}
