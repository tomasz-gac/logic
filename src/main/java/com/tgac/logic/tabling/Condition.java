package com.tgac.logic.tabling;

// ABOUTME: A cell value in the constraint semiring: an answer's proven regions as a
// ABOUTME: DNF of residue conjuncts kept maximal by absorption; ground truth is 1.

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.unification.LVar;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import java.util.List;

/**
 * How much of a term's space an entry has PROVEN, as a value: a disjunction
 * of residue conjuncts — each conjunct one derivation's region, each factor
 * one store's knowledge in canonical names. ⊕ is region union kept in
 * ABSORPTION NORMAL FORM ({@code a ∨ (a ∧ b) = a}): a dominated conjunct
 * contributes nothing and drops, a dominating newcomer evicts what it
 * covers — subsumption dedup is this ⊕'s absorption, not a separate
 * mechanism. ⊗ is conjunction: the cross product of pairwise factor meets
 * ({@link Absorbable#meet}). 1 is TRUE — the single empty conjunct, a
 * GROUND answer — and {@code 1 ⊕ a = 1} makes {@link #RING} BOUNDED: a
 * value that reached 1 can never move, which is why ground answers stream
 * and conditional answers wait for their seal.
 *
 * <p>Equality is the SET of conjuncts — a DNF is knowledge, not arrival
 * order. The operational ⊗ rides the package (restate + propagation);
 * {@link #and} is that same conjunction as a value, for the algebra and
 * its laws.
 *
 * <p>The NAMESPACE CROSSINGS live here too, beside the algebra: a conjunct
 * enters from a package by {@link #project} (call side, the key citizen)
 * or {@link #normalize} (answer side, walking + slot canonicalization),
 * and leaves by {@link #restate} — the one replay primitive both master
 * seeding and answer delivery run, differing only in the renaming.
 */
public final class Condition implements Semilattice<Condition> {

	/** TRUE — the single empty conjunct, a ground answer, the absorbing top. */
	public static final Condition ONE =
			new Condition(Vector.of(HashMap.empty()), HashSet.of(HashMap.empty()));

	/** FALSE — no region proven; the answer is absent. */
	public static final Condition ZERO = new Condition(Vector.empty(), HashSet.empty());

	/** The constraint semiring; bounded because 1 ⊕ a = 1 is absorption itself. */
	public static final BoundedSemiring<Condition> RING = new Ring();

	/** Conjuncts in arrival order — iteration and delivery read here. */
	private final Vector<Map<Class<?>, Projectable<?>>> conjuncts;
	/** The same conjuncts as knowledge — equality reads here. */
	private final HashSet<Map<Class<?>, Projectable<?>>> members;

	private Condition(Vector<Map<Class<?>, Projectable<?>>> conjuncts,
			HashSet<Map<Class<?>, Projectable<?>>> members) {
		this.conjuncts = conjuncts;
		this.members = members;
	}

	/** One derivation's region; the empty conjunct is {@link #ONE}. */
	public static Condition of(Map<Class<?>, Projectable<?>> residues) {
		if (residues.isEmpty()) {
			return ONE;
		}
		return new Condition(Vector.of(residues), HashSet.of(residues));
	}

	/** ⊕: region union in absorption normal form. Identity-preserving when absorbed. */
	public Condition or(Condition other) {
		Condition result = this;
		for (Map<Class<?>, Projectable<?>> conjunct : other.conjuncts) {
			result = result.orConjunct(conjunct);
		}
		return result;
	}

	private Condition orConjunct(Map<Class<?>, Projectable<?>> conjunct) {
		if (members.contains(conjunct)) {
			return this;
		}
		for (Map<Class<?>, Projectable<?>> mine : conjuncts) {
			if (residuesLeq(conjunct, mine)) {
				return this;
			}
		}
		Vector<Map<Class<?>, Projectable<?>>> kept =
				conjuncts.filter(mine -> !residuesLeq(mine, conjunct));
		return new Condition(kept.append(conjunct),
				HashSet.ofAll(kept).add(conjunct));
	}

	/** ⊗: the cross product of pairwise factor meets, re-normalized by ⊕. */
	public Condition and(Condition other) {
		Condition result = ZERO;
		for (Map<Class<?>, Projectable<?>> a : conjuncts) {
			for (Map<Class<?>, Projectable<?>> b : other.conjuncts) {
				result = result.orConjunct(meetConjunct(a, b));
			}
		}
		return result;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Map<Class<?>, Projectable<?>> meetConjunct(
			Map<Class<?>, Projectable<?>> a, Map<Class<?>, Projectable<?>> b) {
		Map<Class<?>, Projectable<?>> result = a;
		for (Tuple2<Class<?>, Projectable<?>> factor : b) {
			Projectable<?> mine = result.getOrElse(factor._1, null);
			result = result.put(factor._1, mine == null
					? factor._2
					: (Projectable<?>) ((Absorbable) mine).meet(factor._2));
		}
		return result;
	}

	/** The value hit the top: no future ⊕ can move it — final on arrival. */
	public boolean isOne() {
		return conjuncts.size() == 1 && conjuncts.get(0).isEmpty();
	}

	public Vector<Map<Class<?>, Projectable<?>>> conjuncts() {
		return conjuncts;
	}

	/**
	 * {@code a ⊑ b} pointwise over store classes, absent = ⊤: every class b
	 * knows about, a must know at least as strongly. The containment check
	 * behind entry reuse (a caller may use an entry whose region covers its
	 * own) and this ⊕'s absorption.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean residuesLeq(Map<Class<?>, Projectable<?>> a, Map<Class<?>, Projectable<?>> b) {
		for (Tuple2<Class<?>, Projectable<?>> knowledge : b) {
			Projectable<?> mine = a.getOrElse(knowledge._1, null);
			if (mine == null || !((PartialOrder) mine).leq(knowledge._2)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The call-side crossing: the caller's constraint knowledge about
	 * {@code callVars}, projected per store into one canonical conjunct —
	 * the key citizen that joins the {@link Call}. A store that cannot
	 * project cannot enter the key, and unkeyed knowledge means silently
	 * wrong reuse — refused loudly. An EMPTY projection (nothing known
	 * about the call vars) stays out of the conjunct, so calls under
	 * irrelevant knowledge stay constraint-free variants; caller-private
	 * knowledge is split away — sound by containment, filtered at
	 * consumption.
	 */
	public static Map<Class<?>, Projectable<?>> project(Package callerPkg, List<LVar<?>> callVars) {
		Map<Class<?>, Projectable<?>> residues = HashMap.empty();
		for (Packaged store : callerPkg.getStores().values()) {
			if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
				continue;
			}
			if (!(store instanceof Projectable)) {
				throw new IllegalStateException(
						"Tabling cannot key constraints it cannot project: non-empty "
								+ store.getClass().getSimpleName() + " at a tabled call");
			}
			Projectable<?> keyed = ((Projectable<?>) store).project(callVars);
			if (!keyed.isEmpty()) {
				residues = residues.put(store.getClass(), keyed);
			}
		}
		return residues;
	}

	/**
	 * The answer-side crossing: each store's factor normalized against the
	 * answer's substitutions (spent entries drop — the ground-answer fast
	 * path is a factor that normalizes to empty), then slot-canonicalized:
	 * live hole vars go to their slot holes, so residues from SEPARATE
	 * derivations compare in ONE basis (dedup, key equality); body locals
	 * keep their names — the existential witnesses ride whole,
	 * conservatively incomparable across answers. Non-projectable live
	 * knowledge refuses loudly.
	 */
	public static Map<Class<?>, Projectable<?>> normalize(Package answerPkg, List<LVar<?>> holeVars) {
		Map<Class<?>, Projectable<?>> residues = HashMap.empty();
		Renaming normalization = Renaming.walking(answerPkg.substitution());
		Renaming canonicalization = Renaming.canonical(holeVars);
		for (Packaged store : answerPkg.getStores().values()) {
			if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
				continue;
			}
			if (!(store instanceof Projectable)) {
				throw new IllegalStateException(
						"Tabling does not support non-projectable store: non-empty "
								+ store.getClass().getSimpleName() + " on a tabled answer");
			}
			Projectable<?> normalized = ((Projectable<?>) store).rename(normalization);
			if (!normalized.isEmpty()) {
				residues = residues.put(store.getClass(), normalized.rename(canonicalization));
			}
		}
		return residues;
	}

	/**
	 * A conjunct imposing itself under {@code renaming} — the ONE replay
	 * primitive: master seeding renames the key's conjunct back onto the
	 * live call vars ({@code Renaming.ofSlots}); answer delivery renames an
	 * answer's conjunct onto the instantiation's fresh holes
	 * ({@code Renaming.into}, unseeded locals minting — the existential).
	 * Statement stays the driver's: each factor rides {@code Propagation.absorb}.
	 */
	public static Goal restate(Map<Class<?>, Projectable<?>> residues, Renaming renaming) {
		Goal restated = Goal.success();
		for (Tuple2<Class<?>, Projectable<?>> factor : residues) {
			restated = Conjunction.of(restated, Propagation.absorb(factor._2.rename(renaming)));
		}
		return restated;
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
				other.conjuncts.exists(theirs -> residuesLeq(mine, theirs)));
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
