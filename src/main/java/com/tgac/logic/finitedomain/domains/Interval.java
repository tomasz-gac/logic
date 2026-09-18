package com.tgac.logic.finitedomain.domains;

// ABOUTME: The contiguous domain [min, max] over raw values: bounds narrowing
// ABOUTME: through the order seat; enumeration and splitting need the step seat.

import com.tgac.functional.Exceptions;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import io.vavr.collection.Iterator;
import io.vavr.control.Option;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * A dense type (no {@link Discrete} seat) keeps every bounds operation and
 * loses the ones that need stepping: {@link #stream} refuses loudly, and
 * {@link #difference} keeps values it cannot split away — sound, wider.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Interval<T> extends Domain<T> {
	T min;
	T max;
	@EqualsAndHashCode.Exclude
	Comparator<T> order;
	@EqualsAndHashCode.Exclude
	Option<Discrete<T>> step;

	public static <T> Interval<T> of(
			T min,
			T max,
			Comparator<T> order,
			Option<Discrete<T>> step) {
		assert order.compare(min, max) <= 0;
		return new Interval<>(min, max, order, step);
	}

	public static <T> Interval<T> normalized(T a, T b, Comparator<T> order, Option<Discrete<T>> step) {
		return new Interval<>(minValue(a, b, order), maxValue(a, b, order), order, step);
	}

	@Override
	public boolean contains(T value) {
		return order.compare(min, value) <= 0 &&
				order.compare(max, value) >= 0;
	}

	@Override
	public Stream<T> stream() {
		Discrete<T> discrete = step.getOrElseThrow(() -> new IllegalStateException(
				"Cannot enumerate " + this + ": no Discrete instance — a dense interval propagates but does not label"));
		return StreamSupport.stream(Iterator.iterate(min, discrete::next)
						.takeWhile(v -> order.compare(v, max) <= 0)
						.spliterator(), false);
	}

	@Override
	public boolean isEmpty() {
		return false;
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
	public T min() {
		return min;
	}

	@Override
	public T max() {
		return max;
	}

	@Override
	public Domain<T> atLeast(T e) {
		if (order.compare(e, max) > 0) {
			return Empty.instance();
		}
		T newMin = maxValue(e, min, order);
		return order.compare(newMin, max) == 0 ? Singleton.of(max, order, step) : Interval.of(newMin, max, order, step);
	}

	@Override
	public Domain<T> atMost(T e) {
		if (order.compare(e, min) < 0) {
			return Empty.instance();
		}
		T newMax = minValue(e, max, order);
		return order.compare(newMax, min) == 0 ? Singleton.of(min, order, step) : Interval.of(min, newMax, order, step);
	}

	@Override
	public Domain<T> intersect(Domain<T> other) {
		Interval<T> that = this;
		return other.accept(new DomainVisitor<T, Domain<T>>() {
			@Override
			public Domain<T> visit(Empty<T> domain) {
				return domain;
			}

			@Override
			public Domain<T> visit(Singleton<T> domain) {
				return contains(domain.getValue()) ?
						domain :
						Empty.instance();
			}

			@Override
			public Domain<T> visit(Interval<T> domain) {
				T lo = maxValue(min(), other.min(), order);
				T hi = minValue(max(), other.max(), order);
				if (order.compare(lo, hi) == 0) {
					return Singleton.of(lo, order, step);
				} else if (order.compare(lo, hi) <= 0) {
					return new Interval<>(lo, hi, order, step);
				} else {
					return Empty.instance();
				}
			}

			@Override
			public Domain<T> visit(Union<T> domain) {
				return domain.intersect(that);
			}

			@Override
			public Domain<T> visit(EnumeratedDomain<T> domain) {
				return domain.intersect(that);
			}
		});
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		return other.accept(new DomainVisitor<T, Boolean>() {
			@Override
			public Boolean visit(Empty<T> domain) {
				return true;
			}

			@Override
			public Boolean visit(Singleton<T> domain) {
				return !contains(domain.getValue());
			}

			@Override
			public Boolean visit(Interval<T> domain) {
				return order.compare(max, other.min()) < 0 || order.compare(min, other.max()) > 0;
			}

			@Override
			public Boolean visit(Union<T> domain) {
				return domain.getIntervals().toJavaStream()
						.allMatch(v -> isDisjoint(v));
			}

			@Override
			public Boolean visit(EnumeratedDomain<T> domain) {
				return domain.getElements().toJavaStream()
						.noneMatch(v -> contains(v));
			}
		});
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		Interval<T> that = this;
		return other.accept(new DomainVisitor<T, Domain<T>>() {
			@Override
			public Domain<T> visit(Empty<T> domain) {
				return that;
			}

			@Override
			public Domain<T> visit(Singleton<T> domain) {
				T value = domain.getValue();
				if (!that.contains(value)) {
					return that;
				}
				// splitting a point out needs stepping; a dense interval keeps it
				return step.<Domain<T>> map(d -> {
					if (order.compare(value, min) == 0) {
						return Interval.of(d.next(value), max, order, step);
					} else if (order.compare(value, max) == 0) {
						return Interval.of(min, d.prev(value), order, step);
					} else {
						return Union.of(
								Interval.of(min, d.prev(value), order, step),
								Interval.of(d.next(value), max, order, step));
					}
				}).getOrElse(that);
			}

			@Override
			public Domain<T> visit(Interval<T> domain) {
				if (order.compare(other.max(), that.min()) < 0 || order.compare(other.min(), max()) > 0) {
					// No overlap, so the whole current interval is the difference
					return that;
				} else if (order.compare(other.min(), min) <= 0 && order.compare(other.max(), max) >= 0) {
					// Other interval contains the current interval, so the difference is empty
					return Empty.instance();
				}
				// every remaining case trims at other's edge — stepping required;
				// a dense interval keeps what it cannot trim
				return step.<Domain<T>> map(d -> {
					if (order.compare(other.min(), min) > 0 && order.compare(other.max(), max) < 0) {
						// Other interval is inside the current interval
						return Union.of(
								Interval.of(that.min(), d.prev(other.min()), order, step),
								Interval.of(d.next(other.max()), that.max(), order, step));
					} else if (order.compare(other.min(), min) <= 0) {
						// Other interval starts before the current interval and ends somewhere in the middle
						return Interval.of(d.next(other.max()), that.max(), order, step);
					} else {
						// Other interval starts somewhere in the middle of the current interval and ends after it
						return Interval.of(that.min(), d.prev(other.min()), order, step);
					}
				}).getOrElse(that);
			}

			@Override
			public Domain<T> visit(Union<T> domain) {
				return ((Union<T>) other).getIntervals().toJavaStream()
						.map(that::difference)
						.reduce(Domain::intersect)
						.orElseGet(Empty::instance);
			}

			@Override
			public Domain<T> visit(EnumeratedDomain<T> domain) {
				return domain.stream()
						.map(v -> Singleton.of(v, order, step))
						.<Domain<T>> reduce(that,
								Domain::difference,
								Exceptions.throwingBiOp(UnsupportedClassVersionError::new));
			}
		});
	}

	@Override
	public <R> R accept(DomainVisitor<T, R> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		return "[" + min + " → " + max + "]";
	}

	static <T> T minValue(T l, T r, Comparator<T> order) {
		return order.compare(l, r) < 0 ? l : r;
	}

	static <T> T maxValue(T l, T r, Comparator<T> order) {
		return order.compare(l, r) > 0 ? l : r;
	}
}
