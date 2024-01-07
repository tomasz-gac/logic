package com.tgac.logic.fd.domains;
import com.tgac.functional.Exceptions;
import io.vavr.Predicates;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.collection.Traversable;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.Predicate;

import static com.tgac.logic.MiniKanren.zip;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EnumeratedInterval<T extends Comparable<T>> extends FiniteDomain<T> {
	Set<T> elements;

	public static <T extends Comparable<T>> EnumeratedInterval<T> of(Set<T> e) {
		return new EnumeratedInterval<>(e);
	}
	public static EnumeratedInterval<Long> range(Long start, Long endExclusive) {
		return new EnumeratedInterval<>(HashSet.range(start, endExclusive));
	}

	@Override
	public java.util.stream.Stream<T> stream() {
		return elements.toJavaStream();
	}
	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public FiniteDomain<T> dropBefore(Predicate<T> pred) {
		return EnumeratedInterval.of(elements.filter(pred));
	}
	@Override
	public FiniteDomain<T> copyBefore(Predicate<T> pred) {
		return EnumeratedInterval.of(elements.filter(e -> !pred.test(e)));
	}
	@Override
	public T min() {
		return elements.min()
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call min on empty domain"));
	}
	@Override
	public T max() {
		return elements.max()
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "Cannot call min on empty domain"));
	}
	@Override
	public boolean contains(T v) {
		return elements.contains(v);
	}

	@Override
	public boolean isDisjoint(FiniteDomain<T> other) {
		if (isEmpty() || other.isEmpty()) {
			return true;
		} else if (zip(getSingletonElement(), other.getSingletonElement()).isDefined()) {
			return !getSingletonElement().get().equals(other.getSingletonElement().get());
		} else if (this.max().compareTo(other.min()) < 0 || other.max().compareTo(this.min()) < 0) {
			return stream().anyMatch(other::contains);
		} else {
			return false;
		}
	}

	@Override
	public FiniteDomain<T> difference(FiniteDomain<T> other) {
		return other.isEmpty() ? this :
				EnumeratedInterval.of(
						other.getSingletonElement()
								.map(elements::remove)
								.getOrElse(() ->
										elements.filter(other::contains)));
	}
	@Override
	protected FiniteDomain<T> intersect(FiniteDomain<T> other) {
		return Option.of(elements.retainAll(((EnumeratedInterval<T>) other).elements))
				.filter(Predicates.not(Set::isEmpty))
				.map(s -> s.size() == 1 ?
						new SingletonFD<>(s.get()) :
						EnumeratedInterval.of(s))
				.getOrElse(EmptyDomain::new);
	}
	@Override
	protected Option<T> getSingletonElement() {
		return Option.of(elements)
				.filter(s -> s.size() == 1)
				.map(Traversable::get);
	}

	@Override
	public String toString() {
		return "[" + min() + " … " + max() + "]";
	}
}