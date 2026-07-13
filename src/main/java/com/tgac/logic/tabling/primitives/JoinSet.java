package com.tgac.logic.tabling.primitives;

// ABOUTME: A persistent set that is a join-semilattice (join = union) while
// ABOUTME: keeping arrival order for indexed reads — equality is membership.

import com.tgac.functional.algebra.JoinSemilattice;
import io.vavr.collection.HashSet;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * EQUALITY IS KNOWLEDGE, NOT ORDER: two sets with the same elements are the
 * same value, while arrival order is kept as an operational detail (readers
 * resume by index). Join is commutative with respect to that equality —
 * which is exactly the license it grants: elements may be merged from any
 * source in any order. Membership is the element type's own {@code equals};
 * a table's reified answers, for instance, bring alpha-equivalence with them.
 *
 * <p>Representation serves the two hot paths separately: the vector answers
 * indexed reads, the set answers membership (dedup) and equality — invariant:
 * {@code members} holds exactly the vector's elements.
 */
public final class JoinSet<A> implements JoinSemilattice<JoinSet<A>> {

	private static final JoinSet<?> EMPTY = new JoinSet<>(Vector.empty(), HashSet.empty());

	private final Vector<A> order;
	private final HashSet<A> members;

	private JoinSet(Vector<A> order, HashSet<A> members) {
		this.order = order;
		this.members = members;
	}

	@SuppressWarnings("unchecked")
	public static <A> JoinSet<A> empty() {
		return (JoinSet<A>) EMPTY;
	}

	/**
	 * The operational face of join-idempotence: the strict-ascent step.
	 *
	 * @return the grown set, or none when the element is already known
	 */
	public Option<JoinSet<A>> append(A element) {
		if (members.contains(element)) {
			return Option.none();
		}
		return Option.of(new JoinSet<>(order.append(element), members.add(element)));
	}

	@Override
	public JoinSet<A> join(JoinSet<A> other) {
		Vector<A> mergedOrder = order;
		HashSet<A> mergedMembers = members;
		for (A a : other.order) {
			if (!mergedMembers.contains(a)) {
				mergedOrder = mergedOrder.append(a);
				mergedMembers = mergedMembers.add(a);
			}
		}
		return new JoinSet<>(mergedOrder, mergedMembers);
	}

	public A get(int index) {
		return index < order.size() ? order.get(index) : null;
	}

	public int size() {
		return order.size();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JoinSet)) {
			return false;
		}
		return members.equals(((JoinSet<?>) o).members);
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
