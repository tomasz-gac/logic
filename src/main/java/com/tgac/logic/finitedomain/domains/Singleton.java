package com.tgac.logic.finitedomain.domains;
import com.tgac.logic.finitedomain.Domain;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.math.BigInteger;
import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Singleton<T> extends Domain<T> {
	Arithmetic<T> value;

	public static <T> Singleton<T> of(Arithmetic<T> value) {
		return new Singleton<>(value);
	}

	public static Singleton<Integer> of(int value) {
		return of(Arithmetic.of(value));
	}

	public static Singleton<Long> of(long value) {
		return of(Arithmetic.of(value));
	}

	public static Singleton<BigInteger> of(BigInteger value) {
		return of(Arithmetic.of(value));
	}

	@Override
	public Domain<T> dropBefore(Arithmetic<T> e) {
		return e.compareTo(value) > 0 ? Empty.instance() : this;
	}

	@Override
	public Domain<T> copyBefore(Arithmetic<T> e) {
		return e.compareTo(value) >= 0 ? this : Empty.instance();
	}

	@Override
	public Stream<T> stream() {
		return Stream.of(value).map(Arithmetic::getValue);
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public Arithmetic<T> min() {
		return value;
	}

	@Override
	public Arithmetic<T> max() {
		return value;
	}

	@Override
	public boolean contains(T v) {
		return value.getValue().equals(v);
	}
	@Override
	public Domain<T> intersect(Domain<T> other) {
		return Option.of(value.getValue())
				.filter(other::contains)
				.<Domain<T>> map(v -> this)
				.getOrElse(Empty::instance);
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		if (other instanceof Singleton) {
			return !((Singleton<T>) other).value.equals(value);
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
		return other.contains(getValue().getValue()) ?
				Empty.instance() : this;
	}

	@Override
	public String toString() {
		return "[" + value.getValue() + "]";
	}

}
