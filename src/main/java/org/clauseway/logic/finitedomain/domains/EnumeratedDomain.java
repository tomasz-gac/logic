package org.clauseway.logic.finitedomain.domains;

// ABOUTME: The explicit-element domain: a sorted, deduplicated array of raw
// ABOUTME: values — gapped by construction, enumerable without any step seat.

import org.clauseway.functional.Exceptions;
import org.clauseway.logic.finitedomain.Bound;
import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * CANONICAL FORM: the construction door sorts by the order seat and drops
 * comparison-equal duplicates, so one value set has one spelling —
 * {@code {3, 1, 2}} IS {@code {1, 2, 3}} — the identity the equal-domain
 * guard and answer keys rely on, and the invariant every binary search
 * here stands on. Narrowings and set operations only ever shrink a
 * canonical array, so they stay canonical without re-sorting.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EnumeratedDomain<T> extends Domain<T> {
	Array<T> elements;
	@EqualsAndHashCode.Exclude
	Comparator<T> order;
	@EqualsAndHashCode.Exclude
	Option<Discrete<T>> step;

	/** Canonical: an empty argument is the Empty domain, one element a Singleton. */
	public static <T> Domain<T> of(Iterable<T> e, Comparator<T> order, Option<Discrete<T>> step) {
		return normalized(canonical(Array.ofAll(e), order), order, step);
	}

	private static <T> Array<T> canonical(Array<T> raw, Comparator<T> order) {
		Array<T> sorted = raw.sorted(order);
		List<T> distinct = new ArrayList<>(sorted.size());
		for (T value : sorted) {
			if (distinct.isEmpty() || order.compare(distinct.get(distinct.size() - 1), value) != 0) {
				distinct.add(value);
			}
		}
		return distinct.size() == sorted.size() ? sorted : Array.ofAll(distinct);
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
		int index = search(bound.getValue());
		int from = index >= 0 ?
				(bound.isIncluded() ? index : index + 1) :
				-(index + 1);
		return normalized(elements.subSequence(from, elements.size()), order, step);
	}

	@Override
	public Domain<T> atMost(Bound<T> bound) {
		int index = search(bound.getValue());
		int to = index >= 0 ?
				(bound.isIncluded() ? index + 1 : index) :
				-(index + 1);
		return normalized(elements.subSequence(0, to), order, step);
	}

	/**
	 * Binary search over the canonical array, no copy: the found index, or
	 * {@code -(insertionPoint) - 1} — {@link java.util.Collections#binarySearch}'s
	 * contract.
	 */
	private int search(T value) {
		int lo = 0;
		int hi = elements.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			int c = order.compare(elements.get(mid), value);
			if (c < 0) {
				lo = mid + 1;
			} else if (c > 0) {
				hi = mid - 1;
			} else {
				return mid;
			}
		}
		return -(lo + 1);
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
		return search(v) >= 0;
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		return elements.toJavaStream()
				.noneMatch(other::contains);
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		return normalized(elements.toJavaStream()
						.filter(v -> !other.contains(v))
						.collect(Array.collector()),
				order, step);
	}

	@Override
	public Domain<T> intersect(Domain<T> other) {
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
