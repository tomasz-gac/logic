package com.tgac.logic.finitedomain.domains;

// ABOUTME: The contiguous domain between two Bounds: open/closed endpoints carry
// ABOUTME: strictness; a step seat canonicalizes every bound to closed form.

import com.tgac.functional.Exceptions;
import com.tgac.logic.finitedomain.Bound;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import io.vavr.collection.Iterator;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * CANONICAL FORM: a type with a step seat normalizes every open bound to
 * its closed spelling at the construction and narrowing doors — {@code (1, 5)}
 * over integers IS {@code [2, 4]} — so one value set has one spelling, the
 * identity the equal-domain termination guard and answer keys rely on. A
 * dense type keeps its open bounds as genuine information: strict orders
 * narrow sharply and difference cuts points out, with no stepping anywhere.
 * What a dense interval still cannot do is enumerate — {@link #stream}
 * refuses loudly.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Interval<T> extends Domain<T> {
	Bound<T> lower;
	Bound<T> upper;
	@EqualsAndHashCode.Exclude
	Comparator<T> order;
	@EqualsAndHashCode.Exclude
	Option<Discrete<T>> step;

	public static <T> Interval<T> of(
			Bound<T> lower,
			Bound<T> upper,
			Comparator<T> order,
			Option<Discrete<T>> step) {
		Bound<T> lo = lowerNormalized(lower, step);
		Bound<T> hi = upperNormalized(upper, step);
		assert Bound.viable(lo, hi, order);
		return new Interval<>(lo, hi, order, step);
	}

	public static <T> Interval<T> of(
			T min,
			T max,
			Comparator<T> order,
			Option<Discrete<T>> step) {
		return of(Bound.closed(min), Bound.closed(max), order, step);
	}

	static <T> Bound<T> lowerNormalized(Bound<T> b, Option<Discrete<T>> step) {
		return b.isIncluded() ? b :
				step.map(d -> Bound.closed(d.next(b.getValue()))).getOrElse(b);
	}

	static <T> Bound<T> upperNormalized(Bound<T> b, Option<Discrete<T>> step) {
		return b.isIncluded() ? b :
				step.map(d -> Bound.closed(d.prev(b.getValue()))).getOrElse(b);
	}

	@Override
	public boolean contains(T value) {
		return lower.asLowerAdmits(value, order) &&
				upper.asUpperAdmits(value, order);
	}

	@Override
	public Stream<T> stream() {
		Discrete<T> discrete = step.getOrElseThrow(() -> new IllegalStateException(
				"Cannot enumerate " + this + ": no Discrete instance — a dense interval propagates but does not label"));
		return StreamSupport.stream(Iterator.iterate(lower.getValue(), discrete::next)
						.takeWhile(v -> order.compare(v, upper.getValue()) <= 0)
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
	public Bound<T> lower() {
		return lower;
	}

	@Override
	public Bound<T> upper() {
		return upper;
	}

	@Override
	public Domain<T> atLeast(Bound<T> bound) {
		return piece(Bound.tighterLower(lowerNormalized(bound, step), lower, order), upper);
	}

	@Override
	public Domain<T> atMost(Bound<T> bound) {
		return piece(lower, Bound.tighterUpper(upperNormalized(bound, step), upper, order));
	}

	/** The domain between two already-normalized bounds: empty, a point, or an interval. */
	private Domain<T> piece(Bound<T> lo, Bound<T> hi) {
		if (!Bound.viable(lo, hi, order)) {
			return Empty.instance();
		}
		if (Bound.point(lo, hi, order)) {
			return Singleton.of(lo.getValue(), order, step);
		}
		return new Interval<>(lo, hi, order, step);
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
				return piece(
						Bound.tighterLower(lower, domain.lower(), order),
						Bound.tighterUpper(upper, domain.upper(), order));
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
				return !(Bound.overlaps(upper, domain.lower(), order)
						&& Bound.overlaps(domain.upper(), lower, order));
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
				// the point leaves: what remains below it and above it
				return remainder(
						upperNormalized(Bound.open(value), step),
						lowerNormalized(Bound.open(value), step));
			}

			@Override
			public Domain<T> visit(Interval<T> domain) {
				if (that.isDisjoint(domain)) {
					return that;
				}
				// what remains below other's lower edge and above its upper —
				// each cut is the complement of the removed interval's bound
				return remainder(
						upperNormalized(domain.lower().complement(), step),
						lowerNormalized(domain.upper().complement(), step));
			}

			/** The parts of this interval below {@code hi} and above {@code lo}. */
			private Domain<T> remainder(Bound<T> hi, Bound<T> lo) {
				List<Domain<T>> parts = new ArrayList<>();
				Domain<T> below = piece(lower, Bound.tighterUpper(hi, upper, order));
				Domain<T> above = piece(Bound.tighterLower(lo, lower, order), upper);
				if (!below.isEmpty()) {
					parts.add(below);
				}
				if (!above.isEmpty()) {
					parts.add(above);
				}
				return parts.isEmpty() ? Empty.instance() :
						parts.size() == 1 ? parts.get(0) :
								Union.of(parts.get(0), parts.get(1));
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
		return (lower.isIncluded() ? "[" : "(")
				+ lower.getValue() + " → " + upper.getValue()
				+ (upper.isIncluded() ? "]" : ")");
	}
}
