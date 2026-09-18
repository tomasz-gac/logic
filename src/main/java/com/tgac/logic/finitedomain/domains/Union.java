package com.tgac.logic.finitedomain.domains;

// ABOUTME: The gapped domain: disjoint members kept sorted and merged where they
// ABOUTME: touch — adjacency needs the members' step seat, overlap only their order.

import static io.vavr.Predicates.not;

import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Value;

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
				.filter(not(Domain::isEmpty))
				.flatMap(fd -> (fd instanceof Union) ?
						((Union<T>) fd).intervals :
						Array.of(fd));

		if (intervals.isEmpty()) {
			return intervals;
		}
		Comparator<T> order = intervals.get(0).order();
		intervals = intervals.sortBy(order, Domain::min);

		List<Domain<T>> mergedIntervals = new ArrayList<>(intervals.size());
		Domain<T> currentInterval = intervals.get(0);

		for (int i = 1; i < intervals.size(); i++) {
			Domain<T> processedInterval = intervals.get(i);
			// a step seat extends the reach by one unit — discrete adjacency;
			// otherwise the bounds themselves decide whether the members touch
			Domain<T> current = currentInterval;
			Bound<T> reach = current.step()
					.map(d -> Bound.closed(d.next(current.max())))
					.getOrElse(current::upper);
			if (Bound.meets(reach, processedInterval.lower(), order)) {
				currentInterval = Interval.of(
						current.lower(),
						Bound.looserUpper(current.upper(), processedInterval.upper(), order),
						order,
						current.step().isDefined() ? current.step() : processedInterval.step());

			} else {
				mergedIntervals.add(currentInterval);
				currentInterval = processedInterval;
			}
		}
		mergedIntervals.add(currentInterval);
		mergedIntervals = mergedIntervals.stream()
				.map(d -> d instanceof Interval && Bound.point(d.lower(), d.upper(), order) ?
						Singleton.of(d.min(), d.order(), d.step()) :
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
	public Comparator<T> order() {
		return intervals.head().order();
	}

	@Override
	public Option<Discrete<T>> step() {
		return intervals.head().step();
	}

	@Override
	public Bound<T> lower() {
		return intervals.head().lower();
	}

	@Override
	public Bound<T> upper() {
		return intervals.last().upper();
	}

	@Override
	public Domain<T> atLeast(Bound<T> bound) {
		return onEachInterval(i -> i.atLeast(bound));
	}

	@Override
	public Domain<T> atMost(Bound<T> bound) {
		return onEachInterval(i -> i.atMost(bound));
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

	private Domain<T> onEachInterval(UnaryOperator<Domain<T>> op) {
		// members can narrow to nothing; an empty member would poison min()/max()
		Array<Domain<T>> result = intervals.toJavaStream()
				.map(op)
				.filter(d -> !d.isEmpty())
				.collect(Array.collector());
		return result.isEmpty() ? Empty.instance() :
				result.size() == 1 ? result.get(0) :
						new Union<>(result);
	}

	@Override
	public String toString() {
		return "∪(" + intervals.toJavaStream()
				.map(Objects::toString)
				.collect(Collectors.joining(", "))
				+ ")";
	}
}
