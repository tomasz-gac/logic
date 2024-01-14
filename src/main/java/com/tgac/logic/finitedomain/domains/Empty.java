package com.tgac.logic.finitedomain.domains;
import com.tgac.logic.finitedomain.Domain;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.stream.Stream;

@SuppressWarnings("rawtypes")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Empty<T> extends Domain<T> {
	private static final Empty INSTANCE = new Empty();

	@SuppressWarnings("unchecked")
	public static <T> Empty<T> instance() {
		return INSTANCE;
	}

	@Override
	public Domain<T> dropBefore(Arithmetic<T> p) {
		return this;
	}
	@Override
	public Domain<T> copyBefore(Arithmetic<T> p) {
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
	public Arithmetic<T> min() {
		throw new UnsupportedOperationException("Cannot call min on empty domain");
	}
	@Override
	public Arithmetic<T> max() {
		throw new UnsupportedOperationException("Cannot call max on empty domain");
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
