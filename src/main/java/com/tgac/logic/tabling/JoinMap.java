package com.tgac.logic.tabling;

// ABOUTME: The answer cell's carrier: keys in arrival order, a semiring-folded value
// ABOUTME: per key, and the append-only log of ascents both reader kinds cursor.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semilattice;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * The free join-semilattice over keyed semiring values: each key's arrivals
 * fold by ⊕, and every arrival that MOVED its key's value — a fresh term, a
 * cheaper cost, a new region — appends to the LOG, one event kind for all
 * of them. {@code order} is the key enumeration (append-only — a value
 * ascent never touches a key's index); {@code log} is the ascent
 * enumeration, the delta journal an inside reader cursors as fixpoint fuel.
 * Under a ⊕ that never moves a stored value the two coincide; they diverge
 * exactly when values can improve, which is when the distinction pays.
 *
 * <p>EQUALITY IS KNOWLEDGE, NOT ORDER: two maps with the same key→value
 * bindings are the same value; both enumerations are operational detail.
 * ⊕ being IDEMPOTENT is the correctness precondition of {@link #append}'s
 * strict-ascent step: a fold that does not grow the entry signals "no new
 * knowledge" — the termination signal the tabling cell needs. Keys bring
 * their own {@code equals}; reified answers carry alpha-equivalence.
 */
public class JoinMap<K, V> implements Semilattice<JoinMap<K, V>> {

	public final Vector<K> order;
	public final HashMap<K, V> members;
	public final Vector<Tuple2<K, V>> log;
	public final IdempotentSemiring<V> semiring;

	private JoinMap(Vector<K> order, HashMap<K, V> members, Vector<Tuple2<K, V>> log,
			IdempotentSemiring<V> semiring) {
		this.order = order;
		this.members = members;
		this.log = log;
		this.semiring = semiring;
	}

	public static <K, V> JoinMap<K, V> empty(IdempotentSemiring<V> semiring) {
		return new JoinMap<>(Vector.empty(), HashMap.empty(), Vector.empty(), semiring);
	}

	/**
	 * The strict-ascent step: fold {@code value} into {@code key}'s entry by
	 * ⊕. A fresh key appends everywhere; a known key folds, grows only if ⊕
	 * moved its value, and keeps its index. Every growth logs the ARRIVAL —
	 * the delta an inside reader re-derives from.
	 *
	 * @return the grown map, or none when the fold did not ascend — no new
	 * 		knowledge, no wake
	 */
	public Option<JoinMap<K, V>> append(K key, V value) {
		Option<V> existing = members.get(key);
		if (existing.isEmpty()) {
			return Option.of(new JoinMap<>(order.append(key), members.put(key, value),
					log.append(Tuple.of(key, value)), semiring));
		}
		V folded = semiring.plus(existing.get(), value);
		if (folded.equals(existing.get())) {
			return Option.none();
		}
		return Option.of(new JoinMap<>(order, members.put(key, folded),
				log.append(Tuple.of(key, value)), semiring));
	}

	@Override
	public JoinMap<K, V> combine(JoinMap<K, V> other) {
		return join(other);
	}

	/**
	 * Direct absorption: every key present in {@code other} with my value
	 * already inside its fold — the ⊕ order answered pointwise, without
	 * replaying a join.
	 */
	@Override
	public boolean absorbedBy(JoinMap<K, V> other) {
		return members.forAll(entry -> other.members.get(entry._1)
				.map(theirs -> semiring.plus(theirs, entry._2).equals(theirs))
				.getOrElse(false));
	}

	/** Replay {@code other}'s arrivals; identity-preserving when all are inert. */
	public JoinMap<K, V> join(JoinMap<K, V> other) {
		JoinMap<K, V> result = this;
		for (Tuple2<K, V> arrival : other.log) {
			result = result.append(arrival._1, arrival._2).getOrElse(result);
		}
		return result;
	}

	/** The key at {@code index} with its current fold, in arrival order. */
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

	public int logSize() {
		return log.size();
	}

	/** The {@code index}-th ascent: the key and the arrival that moved it. */
	public Tuple2<K, V> logAt(int index) {
		return log.get(index);
	}

	/** The value hit ⊕'s top: 1 ⊕ a = 1, so it is final on arrival. */
	public boolean isTop(V value) {
		return semiring.one().equals(value);
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
