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
 */
public final class JoinSet<A> implements JoinSemilattice<JoinSet<A>> {

	private static final JoinSet<?> EMPTY = new JoinSet<>(Vector.empty());

	private final Vector<A> elements;

	private JoinSet(Vector<A> elements) {
		this.elements = elements;
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
		if (elements.contains(element)) {
			return Option.none();
		}
		return Option.of(new JoinSet<>(elements.append(element)));
	}

	@Override
	public JoinSet<A> join(JoinSet<A> other) {
		Vector<A> merged = elements;
		for (A a : other.elements) {
			if (!merged.contains(a)) {
				merged = merged.append(a);
			}
		}
		return new JoinSet<>(merged);
	}

	public A get(int index) {
		return index < elements.size() ? elements.get(index) : null;
	}

	public int size() {
		return elements.size();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JoinSet)) {
			return false;
		}
		return HashSet.ofAll(elements).equals(HashSet.ofAll(((JoinSet<?>) o).elements));
	}

	@Override
	public int hashCode() {
		return HashSet.ofAll(elements).hashCode();
	}

	@Override
	public String toString() {
		return elements.toString();
	}
}
