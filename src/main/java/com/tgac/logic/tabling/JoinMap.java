package com.tgac.logic.tabling;

// ABOUTME: A join-semilattice map: indexed ATOM entries (append-only, cursor-stable)
// ABOUTME: plus a PARTIAL region kept as the maximal antichain under a key Dominance.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semilattice;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * A map into an idempotent-semiring value, as a join-semilattice in two
 * regions decided by the {@link Dominance}:
 *
 * <ul>
 * <li>ATOMS (keys outside the dominance): append-only {@code order}, indexed
 * reads — the cursor-stable enumeration consumers walk by int;</li>
 * <li>the PARTIAL region (keys with comparable extent): kept as the MAXIMAL
 * ANTICHAIN. A dominated newcomer is absorbed (the join's idempotence,
 * generalized); a dominating newcomer EVICTS the keys it covers — and that
 * eviction is an ASCENT, not a removal: the knowledge order is downset
 * inclusion, and the downset grew. Read whole via {@link #partial()},
 * never by index.</li>
 * </ul>
 *
 * <p>EQUALITY IS KNOWLEDGE, NOT ORDER: two maps with the same live
 * key→value bindings are the same value; arrival order is an operational
 * detail. ⊕ being IDEMPOTENT is the correctness precondition of
 * {@link #append}'s strict-ascent step: a fold that does not grow the entry
 * signals "no new knowledge" — the termination signal the tabling cell
 * needs. Keys bring their own {@code equals}; a table's reified answers
 * carry alpha-equivalence.
 *
 * <p>The {@link Dominance} must be antisymmetric up to key equality:
 * mutually-dominating keys must be {@code equals}, or the antichain's
 * representative — and with it map equality — would depend on arrival
 * order. Slot-canonical answer keys satisfy this by construction.
 */
public class JoinMap<K, V> implements Semilattice<JoinMap<K, V>> {

	/** Which keys have comparable extent, and who covers whom. */
	public interface Dominance<K> {

		/** The key participates in the partial region. */
		boolean partial(K key);

		/** {@code a} covers {@code b}: everything b licenses, a licenses. */
		boolean dominates(K a, K b);
	}

	private static final Dominance<Object> DISCRETE = new Dominance<Object>() {
		@Override
		public boolean partial(Object key) {
			return false;
		}

		@Override
		public boolean dominates(Object a, Object b) {
			return false;
		}
	};

	public final Vector<K> order;
	public final HashMap<K, V> members;
	public final IdempotentSemiring<V> semiring;
	/** The partial region's live antichain, in arrival order. */
	private final Vector<K> antichain;
	private final Dominance<K> dominance;

	private JoinMap(Vector<K> order, HashMap<K, V> members, IdempotentSemiring<V> semiring,
			Vector<K> antichain, Dominance<K> dominance) {
		this.order = order;
		this.members = members;
		this.semiring = semiring;
		this.antichain = antichain;
		this.dominance = dominance;
	}

	@SuppressWarnings("unchecked")
	public static <K, V> JoinMap<K, V> empty(IdempotentSemiring<V> semiring) {
		return empty(semiring, (Dominance<K>) DISCRETE);
	}

	public static <K, V> JoinMap<K, V> empty(IdempotentSemiring<V> semiring, Dominance<K> dominance) {
		return new JoinMap<>(Vector.empty(), HashMap.empty(), semiring, Vector.empty(), dominance);
	}

	/**
	 * The strict-ascent step: fold {@code value} into {@code key}'s entry by
	 * ⊕. A known key folds and grows only if ⊕ moved its value. A fresh atom
	 * appends to {@code order}. A fresh partial key takes the ANTICHAIN
	 * step: absorbed when a live key covers it (equivalents keep their
	 * first-arrived representative), otherwise it evicts every live key it
	 * covers and joins the antichain.
	 *
	 * @return the grown map, or none when the fold did not ascend — no new
	 * 		knowledge, no wake
	 */
	public Option<JoinMap<K, V>> append(K key, V value) {
		Option<V> existing = members.get(key);
		if (existing.isDefined()) {
			V folded = semiring.plus(existing.get(), value);
			if (folded.equals(existing.get())) {
				return Option.none();
			}
			return Option.of(new JoinMap<>(order, members.put(key, folded), semiring, antichain, dominance));
		}
		if (!dominance.partial(key)) {
			return Option.of(new JoinMap<>(order.append(key), members.put(key, value), semiring, antichain, dominance));
		}
		for (K live : antichain) {
			if (dominance.dominates(live, key)) {
				return Option.none();
			}
		}
		Vector<K> kept = antichain;
		HashMap<K, V> keptMembers = members;
		for (K live : antichain) {
			if (dominance.dominates(key, live)) {
				kept = kept.remove(live);
				keptMembers = keptMembers.remove(live);
			}
		}
		return Option.of(new JoinMap<>(order, keptMembers.put(key, value), semiring, kept.append(key), dominance));
	}

	@Override
	public JoinMap<K, V> combine(JoinMap<K, V> other) {
		return join(other);
	}

	public JoinMap<K, V> join(JoinMap<K, V> other) {
		JoinMap<K, V> result = this;
		for (K key : other.order) {
			result = result.append(key, other.members.get(key).get()).getOrElse(result);
		}
		for (K key : other.antichain) {
			result = result.append(key, other.members.get(key).get()).getOrElse(result);
		}
		return result;
	}

	/** The indexed atom at {@code index} — partial keys are never indexed. */
	public Tuple2<K, V> get(int index) {
		if (index < order.size()) {
			K key = order.get(index);
			V value = members.get(key).get();
			return Tuple.of(key, value);
		}
		return null;
	}

	/** The atom count — the indexed enumeration's length. */
	public int size() {
		return order.size();
	}

	/** The partial region's live antichain, whole — domination decided it. */
	public Vector<Tuple2<K, V>> partial() {
		return antichain.map(key -> Tuple.of(key, members.get(key).get()));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JoinMap)) {
			return false;
		}
		return members.equals(((JoinMap<?, ?>) o).members);
	}

	@Override
	public int hashCode() {
		return members.hashCode();
	}

	@Override
	public String toString() {
		return antichain.isEmpty() ? order.toString() : order.toString() + "+" + antichain.toString();
	}
}
