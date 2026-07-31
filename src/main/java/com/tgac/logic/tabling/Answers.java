package com.tgac.logic.tabling;

// ABOUTME: The answer cell's value: ground answers in an indexed JoinLog, residue
// ABOUTME: answers in the covered Antichain - one product, routed by the residues.

import com.tgac.functional.algebra.IdempotentSemiring;
import com.tgac.functional.algebra.Semilattice;
import io.vavr.control.Option;

/**
 * What a table entry's channel holds: the PRODUCT of the two answer
 * semilattices, routed by each answer's residues. A ground answer (no
 * residues) is an atom — only equality relates it to its neighbors — and
 * lives in the indexed, append-only {@link JoinLog} that int-cursor
 * consumers walk. A residue-carrying answer has comparable extent — a
 * covers b when the terms are equal and b's residues are pointwise below
 * a's — and lives in the {@link Antichain}, kept maximal, read whole.
 * Joins are componentwise; equality is knowledge over both parts.
 */
public final class Answers<V> implements Semilattice<Answers<V>> {

	private static final Antichain.Dominance<AnswerKey> COVERS = (a, b) ->
			a.getTerm().equals(b.getTerm())
					&& AnswerKey.residuesLeq(b.getResidues(), a.getResidues());

	private final JoinLog<AnswerKey, V> ground;
	private final Antichain<AnswerKey, V> covered;

	private Answers(JoinLog<AnswerKey, V> ground, Antichain<AnswerKey, V> covered) {
		this.ground = ground;
		this.covered = covered;
	}

	public static <V> Answers<V> empty(IdempotentSemiring<V> semiring) {
		return new Answers<>(JoinLog.empty(semiring), Antichain.empty(semiring, COVERS));
	}

	/** The delta step: atoms to the log, extents to the antichain. */
	public Option<Answers<V>> append(AnswerKey key, V value) {
		if (key.getResidues().isEmpty()) {
			return ground.append(key, value).map(grown -> new Answers<>(grown, covered));
		}
		return covered.append(key, value).map(grown -> new Answers<>(ground, grown));
	}

	public JoinLog<AnswerKey, V> ground() {
		return ground;
	}

	public Antichain<AnswerKey, V> covered() {
		return covered;
	}

	@Override
	public Answers<V> combine(Answers<V> other) {
		return new Answers<>(ground.join(other.ground), covered.join(other.covered));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Answers)) {
			return false;
		}
		Answers<?> that = (Answers<?>) o;
		return ground.equals(that.ground) && covered.equals(that.covered);
	}

	@Override
	public int hashCode() {
		return 31 * ground.hashCode() + covered.hashCode();
	}

	@Override
	public String toString() {
		return covered.elements().isEmpty() ? ground.toString() : ground + "+" + covered;
	}
}
