package com.tgac.logic.finitedomain.domains;
import com.tgac.functional.Exceptions;
import com.tgac.logic.finitedomain.Domain;
import io.vavr.collection.Array;
import io.vavr.collection.Iterator;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.math.BigInteger;
import java.util.Collections;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EnumeratedDomain<T> extends Domain<T> {
	Array<Arithmetic<T>> elements;

	public static <T> EnumeratedDomain<T> of(Iterable<Arithmetic<T>> e) {
		return new EnumeratedDomain<>(Array.ofAll(e));
	}

	public static EnumeratedDomain<Long> range(Long start, Long endExclusive) {
		return new EnumeratedDomain<>(Array.range(start, endExclusive).map(Arithmetic::ofCasted));
	}

	public static EnumeratedDomain<Integer> range(int start, int endExclusive) {
		return new EnumeratedDomain<>(Array.range(start, endExclusive).map(Arithmetic::ofCasted));
	}

	public static EnumeratedDomain<BigInteger> range(BigInteger start, BigInteger endExclusive) {
		return new EnumeratedDomain<>(
				Iterator.iterate(start, i -> i.add(BigInteger.ONE))
						.takeWhile(v -> v.compareTo(endExclusive) < 0)
						.map(Arithmetic::of)
						.collect(Array.collector()));
	}

	@Override
	public java.util.stream.Stream<T> stream() {
		return elements.map(Arithmetic::getValue).toJavaStream();
	}

	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public Domain<T> dropBefore(Arithmetic<T> e) {
		if (e.compareTo(max()) > 0) {
			return Empty.instance();
		}
		int index = Collections.binarySearch(elements.toJavaList(), e, Arithmetic::compareTo);
		if (index >= 0) {
			Array<Arithmetic<T>> result = elements.subSequence(index, elements.size());
			return result.isEmpty() ? Empty.instance() :
					result.size() == 1 ? Singleton.of(result.get(0)) :
							EnumeratedDomain.of(result);
		}
		return this;
	}

	@Override
	public Domain<T> copyBefore(Arithmetic<T> e) {
		if (e.compareTo(min()) <= 0) {
			return Empty.instance();
		}
		int index = Collections.binarySearch(elements.toJavaList(), e, Arithmetic::compareTo);
		if (index >= 0) {
			Array<Arithmetic<T>> result = elements.subSequence(0, index);
			return result.isEmpty() ? Empty.instance() :
					result.size() == 1 ?
							Singleton.of(result.get(0)) :
							EnumeratedDomain.of(result);
		}
		return this;
	}
	@Override
	public Arithmetic<T> min() {
		return Option.of(elements)
				.filter(e -> !e.isEmpty())
				.map(e -> e.get(0))
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call min on empty domain"));
	}
	@Override
	public Arithmetic<T> max() {
		return Option.of(elements)
				.filter(e -> !e.isEmpty())
				.map(e -> e.get(e.size() - 1))
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call min on empty domain"));
	}
	@Override
	public boolean contains(T v) {
		return elements.toJavaStream()
				.map(Arithmetic::getValue)
				.anyMatch(v::equals);
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		return elements.toJavaStream()
				.map(Arithmetic::getValue)
				.noneMatch(other::contains);
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		Array<Arithmetic<T>> result = elements.toJavaStream()
				.filter(v -> !other.contains(v.getValue()))
				.collect(Array.collector());
		return result.isEmpty() ? Empty.instance() :
				result.size() == 1 ? Singleton.of(result.get(0)) :
						EnumeratedDomain.of(result);
	}

	@Override
	public Domain<T> intersect(Domain<T> other) {
		Array<Arithmetic<T>> result = elements.toJavaStream()
				.filter(v -> other.contains(v.getValue()))
				.collect(Array.collector());

		return result.isEmpty() ? Empty.instance() :
				result.size() == 1 ? Singleton.of(result.get(0)) :
						EnumeratedDomain.of(result);
	}

	@Override
	public <R> R accept(DomainVisitor<T, R> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		return "[" + min().getValue() + " … " + max().getValue() + "]";
	}
}