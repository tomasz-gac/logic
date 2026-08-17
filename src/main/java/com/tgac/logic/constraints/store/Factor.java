package com.tgac.logic.constraints.store;

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.unification.Any;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface Factor<S extends Factor<S>> extends Packaged, Semilattice<S>, PartialOrder<S> {

	/**
	 * The statement meet: parks {@code c} in the family — pure, context-free,
	 * the combine half of statement; {@link #stated} examines it once a
	 * context is present.
	 */
	S meet(Atom<S> c);

	/**
	 * Scheduled for deletion into {@code leq}: containment of an atom is
	 * entailment of its singleton factor.
	 */
	boolean contains(Atom<S> c);

	/**
	 * Whether the store currently holds no constraints.
	 */
	boolean isEmpty();

	/**
	 * The factor's knowledge as syntax: its atoms, as a plan-space value.
	 * The crossing out of execution space; absorb is the way back (fold
	 * {@code meet(Atom)}, then normalize under the destination's context).
	 */
	Theory<S> theory();

	/** The store meet: the factor product. Accumulation descends the extension. */
	S meet(S other);

	@Override
	default S combine(S other) {
		return meet(other);
	}

	/**
	 * Entailment as the algebra reads it: this ⊑ other iff meeting other adds
	 * nothing. Correct only where {@code equals} compares NORMAL FORMS — a
	 * family holding denormalized factors must override.
	 */
	@Override
	default boolean leq(S other) {
		return meet(other).equals(this);
	}

	/**
	 * Re-establish normal form against {@code state} after a meet brought in
	 * foreign knowledge: re-verify it (a violated record or an out-of-domain
	 * binding FAILS), take first examinations, run the internal fixpoint.
	 * Same scheduling and routing contract as {@code revise}. A met factor
	 * answers no queries before its normalization ran — meet is completed by
	 * normalize.
	 */
	Fiber<Revision> normalize(Package state);

	/**
	 * Revise this store against newly applied bindings — AC-3's REVISE, cKanren's
	 * process-prefix. The chokepoint has already applied the extension; the store
	 * may read anything and change only its own factor — a whole package is not
	 * expressible in the return type. The reaction is COMPLETE: custody checks,
	 * re-examining this store's own watchers of the newly bound variables, and
	 * chasing the resulting cascade are all this store's business; the driver
	 * routes only the returned consequences (inferred prefixes, runs).
	 *
	 * <p>The fiber return is the scheduling contract: cheap reactions return
	 * {@code Fiber.done(revision)}; expensive ones (long cascades, heavy global
	 * propagators) defer between steps — see {@code Worklist} — so the driving
	 * scheduler interleaves other branches fairly. Granularity is the author's
	 * choice; the driver guarantees fairness only between fiber steps.
	 * Termination is the store's contraction obligation: updates may only shrink
	 * knowledge ({@code DomainUpdate} guarantees it for domains).
	 *
	 * @param prefix - exactly the newly applied bindings
	 * @param state - the extended live package to verify and read domains against
	 */
	Fiber<Revision> normalize(Prefix prefix, Package state);

	/**
	 * Commit this store's constraints before {@code x} is reified: finite domains
	 * label their variables to ground values, projections fail on anything still
	 * unrun, disequalities have nothing to force. Runs once per answer, at the end
	 * of the search — not during propagation. (cKanren's enforce-constraints,
	 * Alvis et al.)
	 *
	 * @param x - the variable about to be reified
	 */
	<T> Goal enforce(Term<T> x);

	/**
	 * The statement delta: {@code item} was just parked ({@code meet(Atom)}
	 * did the combine half) and only it is new. The agreement law binds this
	 * to the wholesale pass — {@code stated(atom, P) == normalize(P)} where P
	 * already holds the atom parked — and the default IS the wholesale pass;
	 * families override with a first-examination fast path (examine one
	 * item, not the family).
	 */
	default Fiber<Revision> stated(Atom<S> item, Package state) {
		return normalize(state);
	}

	/**
	 * Render this store's residual constraints into the reified answer: after the
	 * answer term is renamed, each store attaches whatever still constrains it —
	 * disequality its surviving records, finite domains nothing (enforce grounded
	 * them). (cKanren's reify-constraints, Alvis et al.)
	 *
	 * @param unifiable - the reified answer built so far
	 * @param renaming - the crossing into the answer namespace
	 */
	<A> Term<A> reify(Term<A> unifiable, Renaming renaming, Package p);

	/**
	 * Lossless factoring: (the knowledge expressible over {@code vars}, the
	 * remainder) — {@code _1 ∧ _2 = this}. The store decides what is
	 * separable (custody); the CALLER decides what to do with the halves:
	 * keys keep {@code _1} and discard the caller-private remainder.
	 */
	Tuple2<S, S> split(List<LVar<?>> vars);

	/**
	 * This store's knowledge under changed names. A {@link Fiber} because
	 * term rewriting rides the engine's traversals — callers compose, never
	 * {@code get}.
	 */
	Fiber<S> rename(Renaming renaming);

	/**
	 * This store's knowledge about the mapped vars in canonical names — each
	 * var to its any, the correspondence reify built, carried as data.
	 * The comparable key citizen. Projecting an empty map of an empty store
	 * is the empty store: the triviality test is {@code isEmpty}.
	 */
	default Fiber<S> project(Map<LVar<?>, Any<?>> slots) {
		return split(new ArrayList<>(slots.keySet()))._1.rename(Renaming.of(slots));
	}

}
