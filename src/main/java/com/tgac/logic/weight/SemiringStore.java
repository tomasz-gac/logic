package com.tgac.logic.weight;

// ABOUTME: One accumulated value per participating semiring, keyed by the ring
// ABOUTME: itself — the product semiring reified, so one pass computes many things.

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.IdempotentSemiring;
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

	/**
	 * The product as an {@link IdempotentSemiring} — its ⊕ is idempotent
	 * because every component's is, which is what tabling's answer cell demands
	 * (a non-idempotent component would never let the fixpoint converge).
	 */
	public static IdempotentSemiring<SemiringStore> idempotentProduct(IdempotentSemiring<?>... rings) {
		Array<Semiring<?>> asSemirings = Array.empty();
		for (IdempotentSemiring<?> ring : rings) {
			asSemirings = asSemirings.append(ring);
		}
		return new IdempotentProduct(asSemirings);
	}

	/**
	 * The product as a {@link BoundedSemiring} — bounded because every component
	 * is (1 is the top componentwise), so its star is the degenerate {@code a* =
	 * 1} and streaming tabling terminates. The type {@code solveBounded} demands.
	 */
	public static BoundedSemiring<SemiringStore> boundedProduct(BoundedSemiring<?>... rings) {
		Array<Semiring<?>> asSemirings = Array.empty();
		for (BoundedSemiring<?> ring : rings) {
			asSemirings = asSemirings.append(ring);
		}
		return new BoundedProduct(asSemirings);
	}

	/**
	 * The product as a {@link ClosedSemiring} — its star is componentwise, so it
	 * closes cycles for a closed-but-unbounded plug (provenance, probability) that
	 * {@code solveClosed} handles and {@code solveBounded} cannot take.
	 */
	public static ClosedSemiring<SemiringStore> closedProduct(ClosedSemiring<?>... rings) {
		Array<Semiring<?>> asSemirings = Array.empty();
		Array<ClosedSemiring<?>> closed = Array.empty();
		for (ClosedSemiring<?> ring : rings) {
			asSemirings = asSemirings.append(ring);
			closed = closed.append(ring);
		}
		return new ClosedProduct(asSemirings, closed);
	}

	@RequiredArgsConstructor
	private static class Product implements Semiring<SemiringStore> {
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

	private static final class IdempotentProduct extends Product implements IdempotentSemiring<SemiringStore> {
		IdempotentProduct(Array<Semiring<?>> rings) {
			super(rings);
		}
	}

	private static final class BoundedProduct extends Product implements BoundedSemiring<SemiringStore> {
		BoundedProduct(Array<Semiring<?>> rings) {
			super(rings);
		}
	}

	private static final class ClosedProduct extends Product implements ClosedSemiring<SemiringStore> {
		private final Array<ClosedSemiring<?>> closedRings;

		ClosedProduct(Array<Semiring<?>> rings, Array<ClosedSemiring<?>> closedRings) {
			super(rings);
			this.closedRings = closedRings;
		}

		@Override
		public SemiringStore star(SemiringStore a) {
			LinkedHashMap<Semiring<?>, Object> m = LinkedHashMap.empty();
			for (ClosedSemiring<?> ring : closedRings) {
				m = m.put(ring, starEntry(ring, a));
			}
			return new SemiringStore(m);
		}

		private static <S> S starEntry(ClosedSemiring<S> ring, SemiringStore a) {
			return ring.star(a.get(ring));
		}
	}

	private static <S> S plusEntry(Semiring<S> ring, SemiringStore a, SemiringStore b) {
		return ring.plus(a.get(ring), b.get(ring));
	}

	private static <S> S timesEntry(Semiring<S> ring, SemiringStore a, SemiringStore b) {
		return ring.times(a.get(ring), b.get(ring));
	}
}
