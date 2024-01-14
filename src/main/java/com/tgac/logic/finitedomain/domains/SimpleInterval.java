package com.tgac.logic.finitedomain.domains;
import com.tgac.functional.Exceptions;
import io.vavr.collection.Iterator;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.math.BigInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleInterval<T> extends Domain<T> {
	Arithmetic<T> min;
	Arithmetic<T> max;

	public static <T> SimpleInterval<T> of(
			Arithmetic<T> min,
			Arithmetic<T> max) {
		assert min.compareTo(max) <= 0;
		return new SimpleInterval<>(min, max);
	}

	public static SimpleInterval<Integer> of(int min, int max) {
		return of(Arithmetic.of(min), Arithmetic.of(max));
	}

	public static SimpleInterval<Long> of(long min, long max) {
		return of(Arithmetic.of(min), Arithmetic.of(max));
	}

	public static SimpleInterval<BigInteger> of(BigInteger min, BigInteger max) {
		return of(Arithmetic.of(min), Arithmetic.of(max));
	}

	@Override
	public boolean contains(T value) {
		return min.compareToValue(value) <= 0 &&
				max.compareToValue(value) >= 0;
	}
	@Override
	public Stream<T> stream() {
		return StreamSupport.stream(Iterator.iterate(min, Arithmetic::next)
						.takeWhile(v -> v.compareTo(max) <= 0)
						.spliterator(), false)
				.map(Arithmetic::getValue);
	}
	@Override
	public boolean isEmpty() {
		return false;
	}
	@Override
	public Arithmetic<T> min() {
		return min;
	}
	@Override
	public Arithmetic<T> max() {
		return max;
	}
	@Override
	public Domain<T> dropBefore(Arithmetic<T> e) {
		return e.equals(max) ?
				Singleton.of(e) :
				e.compareTo(max) < 0 ?
						SimpleInterval.of(maxValue(e, min), max) :
						Empty.instance();

	}
	@Override
	public Domain<T> copyBefore(Arithmetic<T> e) {
		return e.equals(min) ?
				Empty.instance() :
				e.compareTo(min) > 0 ?
						SimpleInterval.of(min, minValue(e.prev(), max)) :
						Empty.instance();
	}

	@Override
	protected Domain<T> intersect(Domain<T> other) {
		SimpleInterval<T> that = this;
		return other.accept(new DomainVisitor<T, Domain<T>>() {
			@Override
			public Domain<T> visit(Empty<T> instance) {
				return that;
			}
			@Override
			public Domain<T> visit(Singleton<T> instance) {
				return contains(instance.getValue().getValue()) ?
						instance :
						Empty.instance();
			}
			@Override
			public Domain<T> visit(SimpleInterval<T> instance) {
				Arithmetic<T> min = maxValue(min(), other.min());
				Arithmetic<T> max = minValue(max(), other.max());
				if (min.compareTo(max) == 0) {
					return Singleton.of(min);
				} else if (min.compareTo(max) <= 0) {
					return new SimpleInterval<>(min, max);
				} else {
					return Empty.instance();
				}
			}
			@Override
			public Domain<T> visit(MultiInterval<T> instance) {
				return instance.intersect(that);
			}
			@Override
			public Domain<T> visit(EnumeratedInterval<T> instance) {
				return instance.intersect(that);
			}
		});
	}

	@Override
	public boolean isDisjoint(Domain<T> other) {
		return other.accept(new DomainVisitor<T, Boolean>() {
			@Override
			public Boolean visit(Empty<T> instance) {
				return true;
			}
			@Override
			public Boolean visit(Singleton<T> instance) {
				return !contains(instance.getValue().getValue());
			}
			@Override
			public Boolean visit(SimpleInterval<T> instance) {
				return max.compareTo(other.min()) < 0 || min.compareTo(other.max()) > 0;
			}
			@Override
			public Boolean visit(MultiInterval<T> instance) {
				return instance.getIntervals().toJavaStream()
						.allMatch(v -> isDisjoint(v));
			}
			@Override
			public Boolean visit(EnumeratedInterval<T> instance) {
				return instance.getElements().toJavaStream()
						.map(Arithmetic::getValue)
						.noneMatch(v -> contains(v));
			}
		});
	}

	@Override
	public Domain<T> difference(Domain<T> other) {
		SimpleInterval<T> that = this;
		return other.accept(new DomainVisitor<T, Domain<T>>() {
			@Override
			public Domain<T> visit(Empty<T> instance) {
				return that;
			}
			@Override
			public Domain<T> visit(Singleton<T> instance) {
				if (that.contains(instance.getValue().getValue())) {
					return MultiInterval.of(
							SimpleInterval.of(min, instance.getValue().prev()),
							SimpleInterval.of(instance.getValue().next(), max));
				} else {
					return that;
				}
			}
			@Override
			public Domain<T> visit(SimpleInterval<T> instance) {
				if (other.max().compareTo(that.min()) < 0 || other.min().compareTo(max()) > 0) {
					// No overlap, so the whole current interval is the difference
					return that;
				} else if (other.min().compareTo(min) > 0 && other.max().compareTo(max) < 0) {
					// Other interval is inside the current interval
					return MultiInterval.of(
							SimpleInterval.of(that.min(), other.min().prev()),
							SimpleInterval.of(other.max().next(), that.max()));
				} else if (other.min().compareTo(min) <= 0 && other.max().compareTo(max) >= 0) {
					// Other interval contains the current interval, so the difference is empty
					return Empty.instance();
				} else if (other.min().compareTo(min) <= 0) {
					// Other interval starts before the current interval and ends somewhere in the middle
					return SimpleInterval.of(other.max().next(), that.max());
				} else {
					// Other interval starts somewhere in the middle of the current interval and ends after it
					return SimpleInterval.of(that.min(), other.min().prev());
				}
			}
			@Override
			public Domain<T> visit(MultiInterval<T> instance) {
				return ((MultiInterval<T>) other).getIntervals().toJavaStream()
						.map(that::difference)
						.peek(System.out::println)
						.reduce(Domain::intersect)
						.orElseGet(Empty::instance);
			}
			@Override
			public Domain<T> visit(EnumeratedInterval<T> instance) {
				return instance.stream()
						.map(v -> Singleton.of(Arithmetic.ofCasted(v)))
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
		return "[" + min().getValue() + " → " + max().getValue() + "]";
	}

	static <T extends Comparable<T>> T minValue(T l, T r) {
		return l.compareTo(r) < 0 ? l : r;
	}

	static <T extends Comparable<T>> T maxValue(T l, T r) {
		return l.compareTo(r) > 0 ? l : r;
	}
}
