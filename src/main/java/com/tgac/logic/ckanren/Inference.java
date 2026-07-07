package com.tgac.logic.ckanren;

// ABOUTME: The shared cross-factor vocabulary: information a propagator or store has
// ABOUTME: inferred but may not apply itself — bindings grow, domains shrink.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;

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

	/**
	 * The step-1 applier: interpret this inference under the current protocol. The
	 * capability driver (step 2) will pattern-match instead, so it can dedup and
	 * detect contradictions before applying.
	 */
	public abstract Goal toGoal();

	private static final class Bind extends Inference {
		private final Prefix prefix;

		private Bind(Prefix prefix) {
			this.prefix = prefix;
		}

		@Override
		public Goal toGoal() {
			// queue the delta; the agenda's Bind application performs the single
			// revalidation (open -> bind the representative, same -> drop,
			// different -> the branch dies) — the same trichotomy Disequality's
			// record verification reads with the opposite polarity
			return s -> StoreSupport.enqueueBind(prefix, s);
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
		public Goal toGoal() {
			// walk at APPLICATION time: the target may have been bound meanwhile (by an
			// earlier inference of the same verdict, or captured pre-walk by the emitter);
			// narrowing a stale var object would re-bind a bound variable — exactly the
			// violation the chokepoint's full-map contract forbids
			return s -> narrowing.applyTo(MiniKanren.walk(s, target))
					.apply(s);
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
