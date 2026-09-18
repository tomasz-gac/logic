package com.tgac.logic.finitedomain.domains;

// ABOUTME: The empty domain — the lattice bottom: absorbing, memberless,
// ABOUTME: seatless; every query on its bounds refuses.

import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import io.vavr.control.Option;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@SuppressWarnings("rawtypes")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Empty<T> extends Domain<T> {
	private static final Empty INSTANCE = new Empty();

	@SuppressWarnings("unchecked")
	public static <T> Empty<T> instance() {
		return INSTANCE;
	}

	@Override
	public Domain<T> atLeast(Bound<T> bound) {
		return this;
	}

	@Override
	public Domain<T> atMost(Bound<T> bound) {
		return this;
	}

	@Override
	public Stream<T> stream() {
		return Stream.empty();
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public Comparator<T> order() {
		throw new UnsupportedOperationException("Cannot call order on empty domain");
	}

	@Override
	public Option<Discrete<T>> step() {
		return Option.none();
	}

	@Override
	public Bound<T> lower() {
		throw new UnsupportedOperationException("Cannot call lower on empty domain");
	}

	@Override
	public Bound<T> upper() {
		throw new UnsupportedOperationException("Cannot call upper on empty domain");
	}

	@Override
	public <R> R accept(DomainVisitor<T, R> v) {
		return v.visit(this);
	}

	@Override
	public boolean contains(T v) {
		return false;
	}

	@Override
	public Domain<T> intersect(Domain<T> other) {
		return this;
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		return true;
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		return other;
	}

	@Override
	public String toString() {
		return "[]";
	}
}
