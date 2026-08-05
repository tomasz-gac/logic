package com.tgac.logic.tabling;

// ABOUTME: One conjunct of constraint knowledge: per-store factors keyed by store
// ABOUTME: class - the ⊗-monoid of the constraint ring, with its namespace crossings.

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
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
 * from a package by {@link #ofRelevant} (call side, the key citizen) or
 * {@link #ofAll} (answer side, walking + slot canonicalization), and
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
	public static Fiber<Residues> ofRelevant(Package callerPkg, java.util.Map<LVar<?>, Hole<?>> callVars) {
		return callerPkg.getStores().values().foldLeft(
						Fiber.<Map<Class<?>, Projectable<?>>> done(HashMap.empty()),
						(acc, store) -> acc.flatMap(residues -> {
							if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
								return Fiber.done(residues);
							}
							if (!(store instanceof Projectable)) {
								throw new IllegalStateException(
										"Tabling cannot key constraints it cannot project: non-empty "
												+ store.getClass().getSimpleName() + " at a tabled call");
							}
							return ((Projectable<?>) store).project(callVars)
									.map(keyed -> keyed.isEmpty()
											? residues
											: residues.put(store.getClass(), keyed));
						}))
				.map(Residues::of);
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
	public static Fiber<Residues> ofAll(Package answerPkg, java.util.Map<LVar<?>, Hole<?>> holeVars) {
		Renaming canonicalization = Renaming.of(holeVars);
		return resolution(answerPkg.substitution()).flatMap(resolution ->
						answerPkg.getStores().values().foldLeft(
								Fiber.<Map<Class<?>, Projectable<?>>> done(HashMap.empty()),
								(acc, store) -> acc.flatMap(residues -> {
									if (!(store instanceof ConstraintStore) || ((ConstraintStore) store).isEmpty()) {
										return Fiber.done(residues);
									}
									if (!(store instanceof Projectable)) {
										throw new IllegalStateException(
												"Tabling does not support non-projectable store: non-empty "
														+ store.getClass().getSimpleName() + " on a tabled answer");
									}
									return normalized((Projectable<?>) store, resolution, canonicalization)
											.map(factor -> factor.isEmpty()
													? residues
													: residues.put(store.getClass(), factor));
								})))
				.map(Residues::of);
	}

	/** One store's answer factor: resolved, then slot-canonical — empty when spent. */
	private static <S extends Projectable<S>> Fiber<Projectable<?>> normalized(
			Projectable<S> store, Renaming resolution, Renaming canonicalization) {
		return store.rename(resolution).flatMap(resolved -> resolved.isEmpty()
				? Fiber.<Projectable<?>> done(resolved)
				: resolved.rename(canonicalization).map(factor -> factor));
	}

	/**
	 * Resolution: every bound name to its current deep meaning — the WALKING
	 * happens here, once, producing plain data; spent entries fall to values
	 * and drop store-side when the seed is applied. {@link Renaming} itself
	 * is a dumb map and never sees a {@link Substitutions}.
	 */
	private static Fiber<Renaming> resolution(Substitutions home) {
		return home.bindings().foldLeft(
						Fiber.<java.util.Map<Term<?>, Term<?>>> done(new java.util.HashMap<>()),
						(acc, binding) -> acc.flatMap(walked ->
								MiniKanren.walkAll(home, (Term<?>) binding._1).map(meaning -> {
									walked.put(binding._1, meaning);
									return walked;
								})))
				.map(Renaming::of);
	}

	/**
	 * This conjunct imposing itself under {@code renaming} — the ONE replay
	 * primitive: master seeding renames the key's conjunct back onto the
	 * live call vars ({@code Renaming.restating}); answer delivery renames it
	 * onto the instantiation's fresh holes ({@code Renaming.minting}, unseeded
	 * locals minting — the existential). Statement stays the driver's: each
	 * factor rides {@code Propagation.absorb}.
	 */
	public Goal restate(Renaming renaming) {
		Goal restated = Goal.success();
		for (Tuple2<Class<?>, Projectable<?>> factor : factors) {
			restated = Conjunction.of(restated, imposed(factor._2, renaming));
		}
		return restated;
	}

	/** One factor's imposition: rename runs in the goal's fiber, absorb states it. */
	private static <S extends Projectable<S>> Goal imposed(Projectable<S> factor, Renaming renaming) {
		return pkg -> Cont.suspend(k -> factor.rename(renaming)
				.flatMap(renamed -> Propagation.absorb(renamed).apply(pkg).apply(k)));
	}

	@Override
	public String toString() {
		return factors.toString();
	}
}
