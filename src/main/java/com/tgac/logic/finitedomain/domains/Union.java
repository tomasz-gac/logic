package com.tgac.logic.finitedomain.domains;
import com.tgac.logic.finitedomain.Domain;
import io.vavr.collection.Array;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tgac.logic.finitedomain.domains.Interval.maxValue;
import static io.vavr.Predicates.not;

@Value
@EqualsAndHashCode(callSuper = true)
public class Union<T> extends Domain<T> {
	Array<Domain<T>> intervals;

	private Union(Array<Domain<T>> intervals) {
		this.intervals = mergeOverlappingIntervals(intervals);
	}

	@SafeVarargs
	public static <T> Union<T> of(Domain<T>... intervals) {
		return new Union<>(Array.of(intervals));
	}

	public static <T> Union<T> of(Array<Domain<T>> intervals) {
		return new Union<>(intervals);
	}

	public static <T> Array<Domain<T>> mergeOverlappingIntervals(Array<Domain<T>> intervals) {
		intervals = intervals
				.filter(not(Empty.class::isInstance))
				.flatMap(fd -> (fd instanceof Union) ?
						((Union<T>) fd).intervals :
						Array.of(fd))
				.sortBy(Arithmetic::compareTo, Domain::min);

		if (intervals.isEmpty()) {
			return intervals;
		}

		List<Domain<T>> mergedIntervals = new ArrayList<>();
		Domain<T> currentInterval = intervals.get(0);

		for (int i = 1; i < intervals.size(); i++) {
			Domain<T> processedInterval = intervals.get(i);
			if (currentInterval.max().next().compareTo(processedInterval.min()) >= 0) {
				currentInterval = Interval.of(
						currentInterval.min(),
						maxValue(currentInterval.max(), processedInterval.max()));

			} else {
				mergedIntervals.add(currentInterval);
				currentInterval = processedInterval;
			}
		}
		mergedIntervals.add(currentInterval);
		mergedIntervals = mergedIntervals.stream()
				.map(d -> d instanceof Interval ?
						d.max().equals(d.min()) ?
								Singleton.of(d.min()) :
								d :
						d)
				.collect(Collectors.toList());

		return Array.ofAll(mergedIntervals);
	}

	@Override
	public boolean contains(T value) {
		return intervals.toJavaStream()
				.anyMatch(i -> i.contains(value));
	}
	@Override
	public Stream<T> stream() {
		return intervals.toJavaStream()
				.flatMap(Domain::stream);
	}
	@Override
	public boolean isEmpty() {
		return intervals.isEmpty();
	}
	@Override
	public Arithmetic<T> min() {
		return intervals.head().min();
	}
	@Override
	public Arithmetic<T> max() {
		return intervals.last().max();
	}
	@Override
	public Domain<T> dropBefore(Arithmetic<T> value) {
		return onEachInterval(i -> i.dropBefore(value));
	}

	@Override
	public Domain<T> copyBefore(Arithmetic<T> value) {
		return onEachInterval(i -> i.copyBefore(value));
	}
	@Override
	public Domain<T> intersect(Domain<T> other) {
		return onEachInterval(i -> i.intersect(other));
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		return intervals.toJavaStream()
				.allMatch(i -> i.isDisjoint(other));
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		return onEachInterval(i -> i.difference(other));
	}

	@Override
	public <R> R accept(DomainVisitor<T, R> v) {
		return v.visit(this);
	}

	private Union<T> onEachInterval(UnaryOperator<Domain<T>> op) {
		return new Union<>(
				intervals.toJavaStream()
						.map(op)
						.collect(Array.collector()));
	}

	@Override
	public String toString() {
		return "∪(" + intervals.toJavaStream()
				.map(Objects::toString)
				.collect(Collectors.joining(", "))
				+ ")";
	}
}
