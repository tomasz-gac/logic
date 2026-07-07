package com.tgac.logic.ckanren.propagator;

// ABOUTME: The shared cross-factor vocabulary: information a propagator or store has
// ABOUTME: inferred but may not apply itself — bindings grow, domains shrink.

import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A message addressed to a factor of the {@link Package}, never to a store
 * (docs/design/capability-constraint-api.md §2.4). Exactly two kinds, matching the
 * two shared factors of the product lattice: {@link #bind} grows the substitution,
 * {@link #narrow} shrinks what a term may be — expressed through the minimal
 * {@link Narrowing} seam, so the vocabulary couples to the shared concept
 * (rule B of the composition model), not to the finite-domain machinery.
 */
public abstract class Inference {

	private Inference() {
	}

	/**
	 * Inferred bindings — a DELTA of newly inferred pairs, revalidated against the
	 * live package at application: a still-unbound variable is bound, one already
	 * bound to the same value is dropped, and one bound to a different value is a
	 * contradiction between constraint domains — the branch fails (never the silent
	 * keep-first of the raw substitution merge).
	 */
	public static Inference bind(Prefix prefix) {
		return new Bind(prefix);
	}

	/**
	 * An inferred narrowing for {@code target}: a ground target becomes a membership
	 * check, an unchanged attribution is a no-op, a collapse binds through the
	 * chokepoint, and an emptied one fails.
	 */
	public static Inference narrow(Term<?> target, Narrowing narrowing) {
		return new Narrow(target, narrowing);
	}

	/** Pure data: the driver interprets inferences, as it does verdicts and revisions. */
	public abstract <R> R match(
			Function<Prefix, R> onBind,
			BiFunction<Term<?>, Narrowing, R> onNarrow);

	private static final class Bind extends Inference {
		private final Prefix prefix;

		private Bind(Prefix prefix) {
			this.prefix = prefix;
		}

		@Override
		public <R> R match(Function<Prefix, R> onBind,
				BiFunction<Term<?>, Narrowing, R> onNarrow) {
			return onBind.apply(prefix);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Bind && prefix.equals(((Bind) o).prefix);
		}

		@Override
		public int hashCode() {
			return prefix.hashCode();
		}

		@Override
		public String toString() {
			return prefix.toString();
		}
	}

	private static final class Narrow extends Inference {
		private final Term<?> target;
		private final Narrowing narrowing;

		private Narrow(Term<?> target, Narrowing narrowing) {
			this.target = target;
			this.narrowing = narrowing;
		}

		@Override
		public <R> R match(Function<Prefix, R> onBind,
				BiFunction<Term<?>, Narrowing, R> onNarrow) {
			return onNarrow.apply(target, narrowing);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Narrow
					&& target.equals(((Narrow) o).target)
					&& narrowing.equals(((Narrow) o).narrowing);
		}

		@Override
		public int hashCode() {
			return 31 * target.hashCode() + narrowing.hashCode();
		}

		@Override
		public String toString() {
			return target + " ⊂ " + narrowing;
		}
	}
}
