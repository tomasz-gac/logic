package com.tgac.logic.tabling;

// ABOUTME: One conjunct of constraint knowledge: per-store factors keyed by store
// ABOUTME: class - the ⊗-monoid of the constraint ring, with its namespace crossings.

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
import io.vavr.collection.Map;
import java.util.List;
import lombok.Value;

/**
 * One REGION of constraint knowledge: per-store factors, conjoined —
 * {@code (Residues, meet, TRUE)} is the ⊗-monoid the constraint ring
 * ({@link Condition}) sums over: a meet-semilattice with top, the pointwise
 * product of the store lattices (absent factor = that store's ⊤).
 * {@code leq} is containment — narrower entails wider, the store-level
 * {@link Absorbable} convention lifted pointwise — so {@code leq} REVERSES
 * the accumulation order of {@code combine = meet}, exactly like the
 * stores it aggregates.
 *
 * <p>The NAMESPACE CROSSINGS live beside the algebra: a conjunct enters
 * from a package by {@link #project} (call side, the key citizen) or
 * {@link #normalize} (answer side, walking + slot canonicalization), and
 * leaves by {@link #restate} — imposing itself under a renaming, each
 * factor riding {@code Propagation.absorb}.
 */
@Value
public class Residues implements Semilattice<Residues>, PartialOrder<Residues> {

	/** The empty conjunct: ⊗'s 1 and the region ⊤ — no knowledge, TRUE. */
	public static final Residues TRUE = new Residues(HashMap.empty());

	Map<Class<?>, Projectable<?>> factors;

	public static Residues of(Map<Class<?>, Projectable<?>> factors) {
		return factors.isEmpty() ? TRUE : new Residues(factors);
	}

	public boolean isTrue() {
		return factors.isEmpty();
	}

	/** ⊗: pointwise factor meet; a class only one side knows joins whole. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Residues meet(Residues other) {
		Map<Class<?>, Projectable<?>> result = factors;
		for (Tuple2<Class<?>, Projectable<?>> factor : other.factors) {
			Projectable<?> mine = result.getOrElse(factor._1, null);
			result = result.put(factor._1, mine == null
					? factor._2
					: (Projectable<?>) ((Absorbable) mine).meet(factor._2));
		}
		return of(result);
	}

	@Override
	public Residues combine(Residues other) {
		return meet(other);
	}

	/** Meet-combine flips absorption: I add nothing iff other is contained in me. */
	@Override
	public boolean absorbedBy(Residues other) {
		return other.leq(this);
	}

	/**
	 * Containment, pointwise over store classes, absent = ⊤: every class
	 * {@code other} knows about, this must know at least as strongly. The
	 * check behind entry reuse (a caller may use an entry whose region
	 * covers its own) and the ring's absorption.
	 */
	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public boolean leq(Residues other) {
		for (Tuple2<Class<?>, Projectable<?>> knowledge : other.factors) {
			Projectable<?> mine = factors.getOrElse(knowledge._1, null);
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
	public static Residues project(Package callerPkg, List<LVar<?>> callVars) {
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
		return of(residues);
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
	public static Residues normalize(Package answerPkg, List<LVar<?>> holeVars) {
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
		return of(residues);
	}

	/**
	 * This conjunct imposing itself under {@code renaming} — the ONE replay
	 * primitive: master seeding renames the key's conjunct back onto the
	 * live call vars ({@code Renaming.ofSlots}); answer delivery renames it
	 * onto the instantiation's fresh holes ({@code Renaming.into}, unseeded
	 * locals minting — the existential). Statement stays the driver's: each
	 * factor rides {@code Propagation.absorb}.
	 */
	public Goal restate(Renaming renaming) {
		Goal restated = Goal.success();
		for (Tuple2<Class<?>, Projectable<?>> factor : factors) {
			restated = Conjunction.of(restated, Propagation.absorb(factor._2.rename(renaming)));
		}
		return restated;
	}

	@Override
	public String toString() {
		return factors.toString();
	}
}
