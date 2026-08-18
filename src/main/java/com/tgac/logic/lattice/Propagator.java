package com.tgac.logic.lattice;

// ABOUTME: The parked constraint schema: an abstract base owning the watched
// ABOUTME: terms, the identity contract, matching, rename and the statement.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Watches;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Traversable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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
 * <p>THE CLASS CONTRACT that licenses the identity: a schema carries NO
 * instance state beyond the terms it watches — the name must uniquely
 * determine the verdict semantics within its family, so two posts of one
 * relation on the same terms are the same knowledge stated twice (the store
 * dedups them), and renamed instances compare equal wherever the renaming
 * agrees. Equality is (family, name, watched terms), final.
 *
 * <p>Postable by construction: every propagator carries its complete
 * statement context — there is no unconfigured state to construct.
 */
public abstract class Propagator<F extends Factor<F>> implements Atom<F> {

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
	protected abstract F empty();

	/**
	 * Born-violated under the current bindings? Failure found at pricing is
	 * failure forever — the check must be monotone under binding growth.
	 * Default: the author claims nothing.
	 */
	protected boolean doomed(Package state) {
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

	/** The statement: registration seeds the family when absent; doom is the schema's. */
	@Override
	public final Posting posting() {
		return Propagation.activate(this,
				p -> p.getStores().containsKey(getFactorClass()) ? p : p.withStore(empty()),
				this::doomed);
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

	/**
	 * The ad-hoc leaf: a schema from its parts, complete at construction.
	 * The name contract is the CALLER's here — the name must uniquely
	 * determine the body's semantics within the family.
	 */
	public static <F extends Factor<F>> Propagator<F> of(
			F empty,
			String name,
			Iterable<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body) {
		return of(empty, name, watchedTerms, body, p -> false);
	}

	/** {@link #of} with the author's doom check. */
	public static <F extends Factor<F>> Propagator<F> of(
			F empty,
			String name,
			Iterable<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body,
			Predicate<Package> doom) {
		return new Leaf<>(Array.ofAll(watchedTerms), empty, name, body, doom);
	}

	private static final class Leaf<F extends Factor<F>> extends Propagator<F> {
		private final F empty;
		private final String name;
		private final BiFunction<Array<? extends Term<?>>, Package, Verdict> body;
		private final Predicate<Package> doom;

		private Leaf(Array<? extends Term<?>> watchedTerms, F empty, String name,
				BiFunction<Array<? extends Term<?>>, Package, Verdict> body,
				Predicate<Package> doom) {
			super(watchedTerms);
			this.empty = empty;
			this.name = name;
			this.body = body;
			this.doom = doom;
		}

		@Override
		public Verdict propagate(Package state) {
			return body.apply(watchedTerms(), state);
		}

		@Override
		public Propagator<F> watching(Array<? extends Term<?>> terms) {
			return new Leaf<>(terms, empty, name, body, doom);
		}

		@Override
		protected F empty() {
			return empty;
		}

		@Override
		protected boolean doomed(Package state) {
			return doom.test(state);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Class<? extends F> getFactorClass() {
			return (Class<? extends F>) empty.getClass();
		}
	}
}
