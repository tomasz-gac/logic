package com.tgac.logic.tabling;

// ABOUTME: The discrete answer fold: an append-only, indexed log of keyed values
// ABOUTME: whose exact duplicates the join absorbs - the cursor-stable enumeration.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semilattice;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * The free join-semilattice over a DISCRETE key order, with an
 * idempotent-semiring value folded per key: an antichain where the only
 * comparability is equality, so nothing is ever absorbed by a neighbor and
 * nothing ever evicts — the enumeration is append-only and indexed, which
 * is exactly what int-cursor consumers need.
 *
 * <p>EQUALITY IS KNOWLEDGE, NOT ORDER: two logs with the same key→value
 * bindings are the same value; arrival order is an operational detail. ⊕
 * being IDEMPOTENT is the correctness precondition of {@link #append}'s
 * strict-ascent step: a fold that does not grow the entry signals "no new
 * knowledge" — the termination signal the tabling cell needs. Keys bring
 * their own {@code equals}; reified answers carry alpha-equivalence.
 */
public class JoinLog<K, V> implements Semilattice<JoinLog<K, V>> {

	public final Vector<K> order;
	public final HashMap<K, V> members;
	public final IdempotentSemiring<V> semiring;

	private JoinLog(Vector<K> order, HashMap<K, V> members, IdempotentSemiring<V> semiring) {
		this.order = order;
		this.members = members;
		this.semiring = semiring;
	}

	public static <K, V> JoinLog<K, V> empty(IdempotentSemiring<V> semiring) {
		return new JoinLog<>(Vector.empty(), HashMap.empty(), semiring);
	}

	/**
	 * The strict-ascent step: fold {@code value} into {@code key}'s entry by
	 * ⊕. A fresh key appends; a known key folds and grows only if ⊕ moved
	 * its value (arrival order untouched — the key keeps its index).
	 *
	 * @return the grown log, or none when the fold did not ascend — no new
	 * 		knowledge, no wake
	 */
	public Option<JoinLog<K, V>> append(K key, V value) {
		Option<V> existing = members.get(key);
		if (existing.isEmpty()) {
			return Option.of(new JoinLog<>(order.append(key), members.put(key, value), semiring));
		}
		V folded = semiring.plus(existing.get(), value);
		if (folded.equals(existing.get())) {
			return Option.none();
		}
		return Option.of(new JoinLog<>(order, members.put(key, folded), semiring));
	}

	@Override
	public JoinLog<K, V> combine(JoinLog<K, V> other) {
		return join(other);
	}

	public JoinLog<K, V> join(JoinLog<K, V> other) {
		JoinLog<K, V> result = this;
		for (K key : other.order) {
			result = result.append(key, other.members.get(key).get()).getOrElse(result);
		}
		return result;
	}

	public Tuple2<K, V> get(int index) {
		if (index < order.size()) {
			K key = order.get(index);
			return Tuple.of(key, members.get(key).get());
		}
		return null;
	}

	public int size() {
		return order.size();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JoinLog)) {
			return false;
		}
		return members.equals(((JoinLog<?, ?>) o).members);
	}

	@Override
	public int hashCode() {
		return members.hashCode();
	}

	@Override
	public String toString() {
		return order.toString();
	}
}
