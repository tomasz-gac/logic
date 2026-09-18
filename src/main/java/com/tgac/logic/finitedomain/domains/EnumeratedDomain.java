package com.tgac.logic.finitedomain.domains;

// ABOUTME: The explicit-element domain: an ordered array of raw values —
// ABOUTME: gapped by construction, enumerable without any step seat.

import com.tgac.functional.Exceptions;
import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EnumeratedDomain<T> extends Domain<T> {
	Array<T> elements;
	@EqualsAndHashCode.Exclude
	Comparator<T> order;
	@EqualsAndHashCode.Exclude
	Option<Discrete<T>> step;

	/** Canonical: an empty argument is the Empty domain, one element a Singleton. Elements must arrive in order. */
	public static <T> Domain<T> of(Iterable<T> e, Comparator<T> order, Option<Discrete<T>> step) {
		return normalized(Array.ofAll(e), order, step);
	}

	@Override
	public Stream<T> stream() {
		return elements.toJavaStream();
	}

	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public Comparator<T> order() {
		return order;
	}

	@Override
	public Option<Discrete<T>> step() {
		return step;
	}

	@Override
	public Domain<T> atLeast(Bound<T> bound) {
		// TODO : .toJavaList() is doing allocation. Also, where is the sorting that's assumed in binary search?
		// TODO : we should probably hold an elements as java array, or maybe use binary search over vavr's type?
		// TODO : Or maybe we should hold some sort of sorted set here?
		int index = Collections.binarySearch(elements.toJavaList(), bound.getValue(), order);
		int from = index >= 0 ?
				(bound.isIncluded() ? index : index + 1) :
				-(index + 1);
		return normalized(elements.subSequence(from, elements.size()), order, step);
	}

	@Override
	public Domain<T> atMost(Bound<T> bound) {
		// TODO : .toJavaList() is doing allocation. Also, where is the sorting that's assumed in binary search?
		// TODO : we should probably hold an elements as java array, or maybe use binary search over vavr's type?
		// TODO : Or maybe we should hold some sort of sorted set here?
		int index = Collections.binarySearch(elements.toJavaList(), bound.getValue(), order);
		int to = index >= 0 ?
				(bound.isIncluded() ? index + 1 : index) :
				-(index + 1);
		return normalized(elements.subSequence(0, to), order, step);
	}

	private static <T> Domain<T> normalized(Array<T> result, Comparator<T> order, Option<Discrete<T>> step) {
		return result.isEmpty() ? Empty.instance() :
				result.size() == 1 ?
						Singleton.of(result.get(0), order, step) :
						new EnumeratedDomain<>(result, order, step);
	}

	@Override
	public Bound<T> lower() {
		return Option.of(elements)
				.filter(e -> !e.isEmpty())
				.map(e -> Bound.closed(e.get(0)))
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call lower on empty domain"));
	}

	@Override
	public Bound<T> upper() {
		return Option.of(elements)
				.filter(e -> !e.isEmpty())
				.map(e -> Bound.closed(e.get(e.size() - 1)))
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call upper on empty domain"));
	}

	@Override
	public boolean contains(T v) {
		return elements.toJavaStream()
				.anyMatch(v::equals);
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		// TODO : this assumes the contains in other is cheap, which is not if it's an EnumeratedDomain
		return elements.toJavaStream()
				.noneMatch(other::contains);
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		// TODO : this assumes the contains in other is cheap, which is not if it's an EnumeratedDomain
		return normalized(elements.toJavaStream()
						.filter(v -> !other.contains(v))
						.collect(Array.collector()),
				order, step);
	}

	@Override
	public Domain<T> intersect(Domain<T> other) {
		// TODO : this assumes the contains in other is cheap, which is not if it's an EnumeratedDomain
		return normalized(elements.toJavaStream()
						.filter(other::contains)
						.collect(Array.collector()),
				order, step);
	}

	@Override
	public <R> R accept(DomainVisitor<T, R> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		return elements.isEmpty() ? "[]" : "[" + min() + " … " + max() + "]";
	}
}
