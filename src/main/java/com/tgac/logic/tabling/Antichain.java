package com.tgac.logic.tabling;

// ABOUTME: The ordered answer fold: keyed values kept as the maximal antichain
// ABOUTME: under a dominance - absorption and eviction are the join's own algebra.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semilattice;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * The free join-semilattice over a genuinely PARTIAL key order, represented
 * by its maximal antichain: a dominated newcomer is absorbed (the join's
 * idempotence, generalized), a dominating newcomer EVICTS the keys it
 * covers — and the eviction is an ASCENT, not a removal: the knowledge
 * order is downset inclusion, and the downset grew. Read whole via
 * {@link #elements()}, never by index — an ascent may replace what an
 * index pointed at, so consumers track WHAT they have seen, not how far.
 *
 * <p>EQUALITY IS KNOWLEDGE: two antichains with the same live key→value
 * bindings are the same value. The {@link Dominance} must be antisymmetric
 * up to key equality — mutually-dominating keys must be {@code equals}, or
 * the representative, and with it equality, would depend on arrival order.
 * Slot-canonical answer keys satisfy this by construction.
 */
public class Antichain<K, V> implements Semilattice<Antichain<K, V>> {

	/** Who covers whom: everything {@code b} licenses, {@code a} licenses. */
	public interface Dominance<K> {
		boolean dominates(K a, K b);
	}

	private final Vector<K> live;
	private final HashMap<K, V> members;
	private final IdempotentSemiring<V> semiring;
	private final Dominance<K> dominance;

	private Antichain(Vector<K> live, HashMap<K, V> members, IdempotentSemiring<V> semiring,
			Dominance<K> dominance) {
		this.live = live;
		this.members = members;
		this.semiring = semiring;
		this.dominance = dominance;
	}

	public static <K, V> Antichain<K, V> empty(IdempotentSemiring<V> semiring, Dominance<K> dominance) {
		return new Antichain<>(Vector.empty(), HashMap.empty(), semiring, dominance);
	}

	/**
	 * The antichain step: a known key folds by ⊕ (grows only on ascent); a
	 * fresh key is absorbed when a live key covers it (equivalents keep
	 * their first-arrived representative), otherwise it evicts every live
	 * key it covers and joins the antichain.
	 *
	 * @return the grown antichain, or none when nothing ascended — no new
	 * 		knowledge, no wake
	 */
	public Option<Antichain<K, V>> append(K key, V value) {
		Option<V> existing = members.get(key);
		if (existing.isDefined()) {
			V folded = semiring.plus(existing.get(), value);
			if (folded.equals(existing.get())) {
				return Option.none();
			}
			return Option.of(new Antichain<>(live, members.put(key, folded), semiring, dominance));
		}
		for (K present : live) {
			if (dominance.dominates(present, key)) {
				return Option.none();
			}
		}
		Vector<K> kept = live;
		HashMap<K, V> keptMembers = members;
		for (K present : live) {
			if (dominance.dominates(key, present)) {
				kept = kept.remove(present);
				keptMembers = keptMembers.remove(present);
			}
		}
		return Option.of(new Antichain<>(kept.append(key), keptMembers.put(key, value), semiring, dominance));
	}

	@Override
	public Antichain<K, V> combine(Antichain<K, V> other) {
		return join(other);
	}

	public Antichain<K, V> join(Antichain<K, V> other) {
		Antichain<K, V> result = this;
		for (K key : other.live) {
			result = result.append(key, other.members.get(key).get()).getOrElse(result);
		}
		return result;
	}

	/** The live antichain, whole — domination decided its membership. */
	public Vector<Tuple2<K, V>> elements() {
		return live.map(key -> Tuple.of(key, members.get(key).get()));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Antichain)) {
			return false;
		}
		return members.equals(((Antichain<?, ?>) o).members);
	}

	@Override
	public int hashCode() {
		return members.hashCode();
	}

	@Override
	public String toString() {
		return live.toString();
	}
}
