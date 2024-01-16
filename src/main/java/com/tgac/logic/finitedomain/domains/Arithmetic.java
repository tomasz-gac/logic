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

	Arithmetic<T> mul(Arithmetic<T> other);

	Arithmetic<T> div(Arithmetic<T> other);

	boolean isZero();

	int compareToValue(T other);

	static <T> Arithmetic<T> min(Arithmetic<T> u, Arithmetic<T> v) {
		return u.compareTo(v) < 0 ? u : v;
	}

	static <T> Arithmetic<T> max(Arithmetic<T> u, Arithmetic<T> v) {
		return u.compareTo(v) > 0 ? u : v;
	}

	static <T> Arithmetic<T> of(
			T value,
			T unit,
			T zero,
			Comparator<T> cmp,
			BinaryOperator<T> add,
			BinaryOperator<T> sub,
			BinaryOperator<T> mul,
			BinaryOperator<T> div) {
		return Value.of(value, unit, zero, cmp, add, sub, mul, div);
	}

	static Arithmetic<Integer> of(int v) {
		return of(v,
				1, 0,
				Integer::compareTo,
				Integer::sum,
				(i, j) -> i - j,
				(i, j) -> i * j,
				(i, j) -> i / j);
	}

	static Arithmetic<Long> of(long v) {
		return of(v,
				1L, 0L,
				Long::compareTo,
				Long::sum,
				(i, j) -> i - j,
				(i, j) -> i * j,
				(i, j) -> i / j);
	}

	static Arithmetic<BigInteger> of(BigInteger v) {
		return of(v,
				BigInteger.ONE, BigInteger.ZERO,
				BigInteger::compareTo,
				BigInteger::add,
				BigInteger::subtract,
				BigInteger::multiply,
				BigInteger::divide);
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
		T zero;
		Comparator<T> cmp;
		BinaryOperator<T> add;
		BinaryOperator<T> sub;
		BinaryOperator<T> mul;
		BinaryOperator<T> div;

		public Value<T> map(UnaryOperator<T> m) {
			return Value.of(m.apply(value), unit, zero, cmp, add, sub, mul, div);
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
		public Arithmetic<T> mul(Arithmetic<T> other) {
			return map(v -> mul.apply(v, other.getValue()));
		}

		@Override
		public Arithmetic<T> div(Arithmetic<T> other) {
			return map(v -> div.apply(v, other.getValue()));
		}
		@Override
		public boolean isZero() {
			return cmp.compare(value, zero) == 0;
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
