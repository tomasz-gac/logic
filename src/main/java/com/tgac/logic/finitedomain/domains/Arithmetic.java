package com.tgac.logic.finitedomain.domains;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
public interface Arithmetic<T> extends Comparable<Arithmetic<T>> {
	T getValue();

	Arithmetic<T> next();

	Arithmetic<T> prev();

	Arithmetic<T> add(Arithmetic<T> other);

	Arithmetic<T> subtract(Arithmetic<T> other);

	int compareToValue(T other);

	static <T> Arithmetic<T> of(
			T value,
			T unit,
			Comparator<T> cmp,
			BinaryOperator<T> add,
			BinaryOperator<T> sub) {
		return Value.of(value, unit, cmp, add, sub);
	}

	static Arithmetic<Integer> of(int v) {
		return of(v, 1, Integer::compareTo, Integer::sum, (i, j) -> i - j);
	}

	static Arithmetic<Long> of(long v) {
		return of(v, 1L, Long::compareTo, Long::sum, (i, j) -> i - j);
	}

	static Arithmetic<BigInteger> of(BigInteger v) {
		return of(v, BigInteger.ONE, BigInteger::compareTo, BigInteger::add, BigInteger::subtract);
	}

	@SuppressWarnings("unchecked")
	static <T> Arithmetic<T> ofCasted(T value) {
		if (value instanceof Integer) {
			return (Arithmetic<T>) Arithmetic.of((Integer) value);
		} else if (value instanceof Long) {
			return (Arithmetic<T>) Arithmetic.of((Long) value);
		} else if (value instanceof BigInteger) {
			return (Arithmetic<T>) Arithmetic.of((BigInteger) value);
		} else {
			throw new IllegalArgumentException("Unsupported type: " + value);
		}
	}

	@lombok.Value
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "of")
	class Value<T> implements Arithmetic<T> {
		T value;
		T unit;
		Comparator<T> cmp;
		BinaryOperator<T> add;
		BinaryOperator<T> sub;

		public Value<T> map(UnaryOperator<T> m) {
			return Value.of(m.apply(value), unit, cmp, add, sub);
		}

		@Override
		public Arithmetic<T> next() {
			return map(v -> add.apply(value, unit));
		}
		@Override
		public Arithmetic<T> prev() {
			return map(v -> sub.apply(value, unit));
		}

		@Override
		public Arithmetic<T> add(Arithmetic<T> other) {
			return map(v -> add.apply(v, other.getValue()));
		}
		@Override
		public Arithmetic<T> subtract(Arithmetic<T> other) {
			return map(v -> sub.apply(v, other.getValue()));
		}

		@Override
		public int compareToValue(T other) {
			return cmp.compare(value, other);
		}

		@Override
		public int compareTo(Arithmetic<T> o) {
			return cmp.compare(value, o.getValue());
		}
	}
}
