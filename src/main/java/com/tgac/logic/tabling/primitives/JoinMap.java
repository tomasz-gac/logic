package com.tgac.logic.tabling.primitives;

// ABOUTME: A persistent map into a join-semilattice value, joined pointwise: keys
// ABOUTME: keep arrival order for indexed reads, values fold by the semiring's ⊕.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.JoinSemilattice;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * A key→value map that is itself a join-semilattice: keys are the answer terms,
 * values fold by the injected {@link IdempotentSemiring}'s ⊕ (a join, because
 * idempotent). Set-tabling is the degenerate case where the value carries no
 * information.
 *
 * <p>EQUALITY IS KNOWLEDGE, NOT ORDER: two maps with the same key→value bindings
 * are the same value, while arrival order is kept as an operational detail
 * (readers resume by index). ⊕ being IDEMPOTENT is the correctness precondition
 * of {@link #append}'s strict-ascent step: re-folding a value that does not grow
 * the entry signals "no new knowledge", the termination signal the tabling cell
 * needs — a non-idempotent ⊕ would ascend forever and never signal done. Keys
 * bring their own {@code equals}; a table's reified answers carry alpha-equivalence.
 *
 * <p>Representation serves the two hot paths separately: the vector answers
 * indexed reads, the map answers value lookup and equality — invariant:
 * {@code members} holds exactly the vector's keys.
 */
public class JoinMap<K, V> implements JoinSemilattice<JoinMap<K, V>> {
	public final Vector<K> order;
	public final HashMap<K, V> members;
	public final IdempotentSemiring<V> semiring;

	private JoinMap(Vector<K> order, HashMap<K, V> members, IdempotentSemiring<V> semiring) {
		this.order = order;
		this.members = members;
		this.semiring = semiring;
	}

	public static <K, V> JoinMap<K, V> empty(IdempotentSemiring<V> semiring) {
		return new JoinMap<>(Vector.empty(), HashMap.empty(), semiring);
	}

	/**
	 * The strict-ascent step: fold {@code value} into {@code key}'s entry by ⊕.
	 * A fresh key is a new entry; a known key folds and grows only if ⊕ moved
	 * its value (arrival order is untouched — the key keeps its index).
	 *
	 * @return the grown map, or none when the fold did not ascend (the key was
	 * 		present and ⊕ left its value unchanged) — no new knowledge, no wake
	 */
	public Option<JoinMap<K, V>> append(K key, V value) {
		Option<V> existing = members.get(key);
		if (existing.isEmpty()) {
			return Option.of(new JoinMap<>(order.append(key), members.put(key, value), semiring));
		}
		V folded = semiring.plus(existing.get(), value);
		if (folded.equals(existing.get())) {
			return Option.none();
		}
		return Option.of(new JoinMap<>(order, members.put(key, folded), semiring));
	}

	@Override
	public JoinMap<K, V> join(JoinMap<K, V> other) {
		Vector<K> mergedOrder = order;
		HashMap<K, V> mergedMembers = members;
		for (K key : other.order) {
			if (!mergedMembers.containsKey(key)) {
				mergedOrder = mergedOrder.append(key);
				mergedMembers = mergedMembers.put(key, other.members.get(key).get());
			} else {
				mergedMembers = mergedMembers.put(key,
						semiring.plus(
								mergedMembers.get(key).get(),
								other.members.get(key).get()));
			}
		}
		return new JoinMap<>(mergedOrder, mergedMembers, semiring);
	}

	public Tuple2<K, V> get(int index) {
		if (index < order.size()) {
			K key = order.get(index);
			V value = members.get(key).get();
			return Tuple.of(key, value);
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
		return order.toString();
	}
}
