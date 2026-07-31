package com.tgac.logic.tabling;

// ABOUTME: A cell value in the constraint semiring: an answer's proven regions as a
// ABOUTME: DNF of residue conjuncts kept maximal by absorption; ground truth is 1.

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.logic.constraints.store.Absorbable;
import io.vavr.collection.HashSet;
import io.vavr.collection.Vector;

/**
 * How much of a term's space an entry has PROVEN, as a value: a disjunction
 * of {@link Residues} conjuncts — each one derivation's region. ⊕ is region
 * union kept in ABSORPTION NORMAL FORM ({@code a ∨ (a ∧ b) = a}): a
 * dominated conjunct contributes nothing and drops, a dominating newcomer
 * evicts what it covers — subsumption dedup is this ⊕'s absorption, not a
 * separate mechanism. ⊗ is conjunction: the cross product of pairwise
 * conjunct meets ({@link Absorbable#meet} pointwise). 1 is TRUE — the
 * single empty conjunct, a GROUND answer — and {@code 1 ⊕ a = 1} makes
 * {@link #RING} BOUNDED: a value that reached 1 can never move, which is
 * why ground answers stream and conditional answers wait for their seal.
 *
 * <p>Equality is the SET of conjuncts — a DNF is knowledge, not arrival
 * order. The operational ⊗ rides the package ({@link Residues#restate} +
 * propagation); {@link #and} is that same conjunction as a value, for the
 * algebra and its laws.
 */
public final class Condition implements Semilattice<Condition> {

	/** TRUE — the single empty conjunct, a ground answer, the absorbing top. */
	public static final Condition ONE =
			new Condition(Vector.of(Residues.TRUE), HashSet.of(Residues.TRUE));

	/** FALSE — no region proven; the answer is absent. */
	public static final Condition ZERO = new Condition(Vector.empty(), HashSet.empty());

	/** The constraint semiring; bounded because 1 ⊕ a = 1 is absorption itself. */
	public static final BoundedSemiring<Condition> RING = new Ring();

	/** Conjuncts in arrival order — iteration and delivery read here. */
	private final Vector<Residues> conjuncts;
	/** The same conjuncts as knowledge — equality reads here. */
	private final HashSet<Residues> members;

	private Condition(Vector<Residues> conjuncts, HashSet<Residues> members) {
		this.conjuncts = conjuncts;
		this.members = members;
	}

	/** One derivation's region; the TRUE conjunct is {@link #ONE}. */
	public static Condition of(Residues residues) {
		if (residues.isTrue()) {
			return ONE;
		}
		return new Condition(Vector.of(residues), HashSet.of(residues));
	}

	/** ⊕: region union in absorption normal form. Identity-preserving when absorbed. */
	public Condition or(Condition other) {
		Condition result = this;
		for (Residues conjunct : other.conjuncts) {
			result = result.orConjunct(conjunct);
		}
		return result;
	}

	private Condition orConjunct(Residues conjunct) {
		if (members.contains(conjunct)) {
			return this;
		}
		for (Residues mine : conjuncts) {
			if (conjunct.leq(mine)) {
				return this;
			}
		}
		Vector<Residues> kept = conjuncts.filter(mine -> !mine.leq(conjunct));
		return new Condition(kept.append(conjunct),
				HashSet.ofAll(kept).add(conjunct));
	}

	/** ⊗: the cross product of pairwise conjunct meets, re-normalized by ⊕. */
	public Condition and(Condition other) {
		Condition result = ZERO;
		for (Residues a : conjuncts) {
			for (Residues b : other.conjuncts) {
				result = result.orConjunct(a.meet(b));
			}
		}
		return result;
	}

	/** The value hit the top: no future ⊕ can move it — final on arrival. */
	public boolean isOne() {
		return conjuncts.size() == 1 && conjuncts.get(0).isTrue();
	}

	public Vector<Residues> conjuncts() {
		return conjuncts;
	}

	@Override
	public Condition combine(Condition other) {
		return or(other);
	}

	/**
	 * Direct absorption: every conjunct of mine dominated by one of
	 * {@code other}'s — the ⊕ order answered without folding the join.
	 */
	@Override
	public boolean absorbedBy(Condition other) {
		return conjuncts.forAll(mine ->
				other.conjuncts.exists(mine::leq));
	}

	/** The constraint ring; star is degenerate through {@link BoundedSemiring}. */
	private static final class Ring implements BoundedSemiring<Condition> {
		@Override
		public Condition zero() {
			return ZERO;
		}

		@Override
		public Condition one() {
			return ONE;
		}

		@Override
		public Condition plus(Condition a, Condition b) {
			return a.or(b);
		}

		@Override
		public Condition times(Condition a, Condition b) {
			return a.and(b);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Condition)) {
			return false;
		}
		return members.equals(((Condition) o).members);
	}

	@Override
	public int hashCode() {
		return members.hashCode();
	}

	@Override
	public String toString() {
		return isOne() ? "1" : conjuncts.isEmpty() ? "0" : conjuncts.toString();
	}
}
