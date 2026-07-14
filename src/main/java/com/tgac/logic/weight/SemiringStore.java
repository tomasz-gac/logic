package com.tgac.logic.weight;

// ABOUTME: One accumulated value per participating semiring, keyed by the ring
// ABOUTME: itself — the product semiring reified, so one pass computes many things.

import com.tgac.functional.algebra.Semiring;
import com.tgac.logic.goals.Packaged;
import io.vavr.collection.Array;
import io.vavr.collection.LinkedHashMap;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * A bundle of weighted computations threaded through one search: each
 * participating {@link Semiring} keeps its own running value, keyed by the ring
 * instance (a computation IS its semiring — holding two of the same makes no
 * sense). Rides the {@link Packaged package} so branches carry independent
 * weights for free.
 *
 * <p>The store is operated on by the PRODUCT semiring ({@link #product}), whose
 * ⊕/⊗ act componentwise. Every store in circulation is COMPLETE — it holds a
 * value for every ring in its product — because they all descend from the
 * product's {@link Semiring#one() one}/{@link Semiring#zero() zero} and its
 * componentwise operations. Build a factor-site weight from {@code one()} and
 * override the rings it speaks to; the rest stay at their neutral {@code one()},
 * so a site says nothing about the computations it does not mention.
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public final class SemiringStore implements Packaged {

	private final LinkedHashMap<Semiring<?>, Object> values;

	/** The value {@code ring} has accumulated in this store. */
	@SuppressWarnings("unchecked")
	public <S> S get(Semiring<S> ring) {
		return (S) values.get(ring)
				.getOrElseThrow(() -> new IllegalArgumentException(
						"store holds no value for " + ring));
	}

	/** This store with {@code ring}'s value replaced by {@code value}. */
	public <S> SemiringStore with(Semiring<S> ring, S value) {
		return new SemiringStore(values.put(ring, value));
	}

	/**
	 * The product semiring over {@code rings}: elements are stores holding one
	 * value per ring, combined componentwise. Lawful whenever each ring is
	 * (a product of semirings is a semiring).
	 */
	public static Semiring<SemiringStore> product(Semiring<?>... rings) {
		return new Product(Array.of(rings));
	}

	@RequiredArgsConstructor
	private static final class Product implements Semiring<SemiringStore> {
		private final Array<Semiring<?>> rings;

		@Override
		public SemiringStore zero() {
			return seed(false);
		}

		@Override
		public SemiringStore one() {
			return seed(true);
		}

		@Override
		public SemiringStore plus(SemiringStore a, SemiringStore b) {
			LinkedHashMap<Semiring<?>, Object> m = LinkedHashMap.empty();
			for (Semiring<?> ring : rings) {
				m = m.put(ring, plusEntry(ring, a, b));
			}
			return new SemiringStore(m);
		}

		@Override
		public SemiringStore times(SemiringStore a, SemiringStore b) {
			LinkedHashMap<Semiring<?>, Object> m = LinkedHashMap.empty();
			for (Semiring<?> ring : rings) {
				m = m.put(ring, timesEntry(ring, a, b));
			}
			return new SemiringStore(m);
		}

		private SemiringStore seed(boolean one) {
			LinkedHashMap<Semiring<?>, Object> m = LinkedHashMap.empty();
			for (Semiring<?> ring : rings) {
				m = m.put(ring, one ? ring.one() : ring.zero());
			}
			return new SemiringStore(m);
		}
	}

	private static <S> S plusEntry(Semiring<S> ring, SemiringStore a, SemiringStore b) {
		return ring.plus(a.get(ring), b.get(ring));
	}

	private static <S> S timesEntry(Semiring<S> ring, SemiringStore a, SemiringStore b) {
		return ring.times(a.get(ring), b.get(ring));
	}
}
