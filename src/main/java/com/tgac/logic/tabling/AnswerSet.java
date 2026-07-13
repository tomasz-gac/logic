package com.tgac.logic.tabling;

// ABOUTME: A table entry's answers as a persistent join-semilattice value:
// ABOUTME: join is union, and join-idempotence IS the dedup discipline.

import com.tgac.functional.algebra.JoinSemilattice;
import com.tgac.logic.unification.Reified;
import io.vavr.collection.HashSet;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * The engine's first native citizen of the GROWING half (the meet side has
 * Domain and the stores): answer sets ascend by union, and the law that
 * terminates both the search and the completion detector — no strict growth,
 * no wake — is join-idempotence, checked by {@code SemilatticeLaws.checkJoin}.
 *
 * <p>EQUALITY IS KNOWLEDGE, NOT ORDER: two answer sets with the same answers
 * are the same value, while insertion order is kept as an operational detail
 * (consumers resume by index). Join is commutative with respect to that
 * equality, which is exactly the license it grants: answers may be merged
 * from any branch in any order. Reified terms carry alpha-equivalence as
 * plain equality, so membership decides answer identity.
 */
public final class AnswerSet implements JoinSemilattice<AnswerSet> {

	private static final AnswerSet EMPTY = new AnswerSet(Vector.empty());

	private final Vector<Reified<?>> answers;

	private AnswerSet(Vector<Reified<?>> answers) {
		this.answers = answers;
	}

	public static AnswerSet empty() {
		return EMPTY;
	}

	/**
	 * The operational face of join-idempotence: the strict-ascent step.
	 *
	 * @return the grown set, or none when the answer is already known
	 */
	public Option<AnswerSet> append(Reified<?> answer) {
		if (answers.contains(answer)) {
			return Option.none();
		}
		return Option.of(new AnswerSet(answers.append(answer)));
	}

	@Override
	public AnswerSet join(AnswerSet other) {
		Vector<Reified<?>> merged = answers;
		for (Reified<?> a : other.answers) {
			if (!merged.contains(a)) {
				merged = merged.append(a);
			}
		}
		return new AnswerSet(merged);
	}

	public Reified<?> answerAt(int index) {
		return index < answers.size() ? answers.get(index) : null;
	}

	public int size() {
		return answers.size();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnswerSet)) {
			return false;
		}
		return HashSet.ofAll(answers).equals(HashSet.ofAll(((AnswerSet) o).answers));
	}

	@Override
	public int hashCode() {
		return HashSet.ofAll(answers).hashCode();
	}

	@Override
	public String toString() {
		return answers.toString();
	}
}
