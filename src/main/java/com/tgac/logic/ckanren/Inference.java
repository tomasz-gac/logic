package com.tgac.logic.ckanren;

// ABOUTME: The shared cross-factor vocabulary: information a propagator or store has
// ABOUTME: inferred but may not apply itself — bindings grow, domains shrink.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
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
	public static Inference bind(HashMap<LVar<?>, Term<?>> delta) {
		return new Bind(delta);
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
		private final HashMap<LVar<?>, Term<?>> delta;

		private Bind(HashMap<LVar<?>, Term<?>> delta) {
			this.delta = delta;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Goal toGoal() {
			return s -> {
				HashMap<LVar<?>, Term<?>> kept = HashMap.empty();
				for (io.vavr.Tuple2<LVar<?>, Term<?>> binding : delta) {
					Term<?> walked = com.tgac.logic.unification.MiniKanren.walk(s, binding._1);
					if (walked.asVar().isDefined()) {
						// still open — bind the live representative
						kept = kept.put((LVar<?>) walked.asVar().get(), binding._2);
					} else if (!walked.equals(binding._2)) {
						// two domains inferred different values: the branch is inconsistent
						return com.tgac.functional.monad.Cont.complete(
								com.tgac.functional.category.Nothing.nothing());
					}
					// bound to the same value: already known, drop
				}
				if (kept.isEmpty()) {
					return com.tgac.functional.monad.Cont.just(s);
				}
				HashMap<LVar<?>, Term<?>> full = s.getSubstitutions();
				for (io.vavr.Tuple2<LVar<?>, Term<?>> binding : kept) {
					full = full.put(binding._1, binding._2);
				}
				return StoreSupport.processPrefix(full).apply(s);
			};
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Bind && delta.equals(((Bind) o).delta);
		}

		@Override
		public int hashCode() {
			return delta.hashCode();
		}

		@Override
		public String toString() {
			return "bind" + delta;
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
			return s -> narrowing.applyTo(com.tgac.logic.unification.MiniKanren.walk(s, target))
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
