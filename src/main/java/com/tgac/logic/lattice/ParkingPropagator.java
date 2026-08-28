package com.tgac.logic.lattice;

// ABOUTME: The parked constraint schema whose examination is a fiber: it may defer
// ABOUTME: or park awaiting a channel before answering its verdict.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Doomed;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Watches;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Traversable;
import java.util.Objects;

/**
 * {@link Propagator}'s fiber-lane sibling: the same parked schema — watched
 * terms, the identity contract, walk-aware watch matching,
 * rename-as-re-instantiation — with an examination that is a {@link Fiber}:
 * {@link #propagate} may defer, or park awaiting a channel, before answering
 * its {@link Verdict}. A synchronous drive against this kind cannot be
 * written; pricing ({@link #doomed}) stays synchronous — keep it cheap.
 *
 * <p>THE CLASS CONTRACT is the sync kind's, unchanged: a schema carries NO
 * instance state beyond the terms it watches — the name must uniquely
 * determine the verdict semantics within its family. Equality is (family,
 * name, watched terms) WITHIN the kind, final: the examination lane is part
 * of the kind, so a parking schema never equals a sync one, whatever they
 * share.
 */
public abstract class ParkingPropagator<F extends Factor<F>> implements Atom<F>, Doomed {

	private final Array<? extends Term<?>> watchedTerms;

	protected ParkingPropagator(Array<? extends Term<?>> watchedTerms) {
		this.watchedTerms = watchedTerms;
	}

	/** Re-examine against the current state, as a fiber. Reads anything, mutates nothing. */
	public abstract Fiber<Verdict> propagate(Package state);

	/**
	 * This schema re-instantiated over other terms — how a carried coupling
	 * replays onto a consumption's fresh variables. The body reads its
	 * variables POSITIONALLY through the watched terms, never through
	 * lexical capture, which is what makes this sound.
	 */
	public abstract ParkingPropagator<F> watching(Array<? extends Term<?>> terms);

	/** The family's empty — the statement's registration seed. */
	@Override
	public abstract F empty();

	/**
	 * Born-violated under the current bindings? Failure found at pricing is
	 * failure forever — the check must be monotone under binding growth.
	 * Default: the author claims nothing.
	 */
	@Override
	public boolean doomed(Package state) {
		return false;
	}

	/** The terms whose variables this propagator watches — as stated, un-walked. */
	public final Array<? extends Term<?>> watchedTerms() {
		return watchedTerms;
	}

	@Override
	public final Traversable<Term<?>> watched() {
		return Array.narrow(watchedTerms);
	}

	/**
	 * Does a change to {@code changed} re-run this propagator? Chain-inclusive:
	 * see {@link Watches}. Watched terms are VARIABLES in practice — a composite
	 * watched term does not trigger on its members' bindings (suspensions use
	 * the structural variant; no FD constraint watches composites).
	 */
	public final boolean watches(Package state, Term<?> changed) {
		for (Term<?> watchedTerm : watchedTerms) {
			if (Watches.matches(state.substitution(), watchedTerm, changed)) {
				return true;
			}
		}
		return false;
	}

	/** The schema re-instantiated over the renamed terms — {@link #watching}. */
	@Override
	public final Fiber<Atom<F>> rename(Renaming renaming) {
		return watchedTerms.foldLeft(
						Fiber.<List<Term<?>>> done(List.empty()),
						(acc, term) -> acc.flatMap(terms ->
								renaming.apply(term).map(terms::append)))
				.map(terms -> watching(Array.ofAll(terms)));
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ParkingPropagator)) {
			return false;
		}
		ParkingPropagator<?> that = (ParkingPropagator<?>) o;
		return getFactorClass().equals(that.getFactorClass())
				&& name().equals(that.name())
				&& watchedTerms.equals(that.watchedTerms);
	}

	@Override
	public final int hashCode() {
		return Objects.hash(getFactorClass(), name(), watchedTerms);
	}

	@Override
	public String toString() {
		return name() + watchedTerms;
	}
}
