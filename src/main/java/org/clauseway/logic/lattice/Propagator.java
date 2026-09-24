package org.clauseway.logic.lattice;

// ABOUTME: The parked constraint schema: an abstract base owning the watched
// ABOUTME: terms, the identity contract, matching, rename and the statement.

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.logic.constraints.store.Atom;
import org.clauseway.logic.constraints.store.Doomed;
import org.clauseway.logic.constraints.store.Factor;
import org.clauseway.logic.constraints.store.Renaming;
import org.clauseway.logic.constraints.store.Watches;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.terms.Term;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Traversable;
import java.util.Objects;

/**
 * The parked unit of the wake machinery (docs/reference/constraint-kernel.md
 * §2.2), as an abstract base: the class IS the schema. Shared here — the one
 * piece of instance state (the watched terms), the identity contract, the
 * walk-aware watch matching, rename-as-re-instantiation and the statement.
 * A subclass supplies exactly its schema: {@link #propagate}, its
 * re-instantiation {@link #watching}, its family ({@link #getFactorClass},
 * {@link #empty}), its {@link #name} and, where the author knows better than
 * never, its {@link #doomed} check.
 *
 * <p>THE CLASS CONTRACT that licenses the identity: a schema carries no
 * instance STATE beyond the terms it watches — stateless, operation-only
 * capability instances (order, arithmetic, stepping) may ride along, but
 * they are determined by the watched terms' value type, so the name still
 * uniquely determines the verdict semantics within its family: two posts of
 * one relation on the same terms are the same knowledge stated twice (the
 * store dedups them), and renamed instances compare equal wherever the
 * renaming agrees. Equality is (family, name, watched terms), final.
 *
 * <p>Postable by construction: every propagator carries its complete
 * statement context — there is no unconfigured state to construct.
 */
public abstract class Propagator<F extends Factor<F>> implements Atom<F>, Doomed {

	private final Array<? extends Term<?>> watchedTerms;

	protected Propagator(Array<? extends Term<?>> watchedTerms) {
		this.watchedTerms = watchedTerms;
	}

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	public abstract Verdict propagate(Package state);

	/**
	 * This schema re-instantiated over other terms — how a carried coupling
	 * replays onto a consumption's fresh variables. The body reads its
	 * variables POSITIONALLY through the watched terms, never through
	 * lexical capture, which is what makes this sound.
	 */
	public abstract Propagator<F> watching(Array<? extends Term<?>> terms);

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
		if (!(o instanceof Propagator)) {
			return false;
		}
		Propagator<?> that = (Propagator<?>) o;
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
