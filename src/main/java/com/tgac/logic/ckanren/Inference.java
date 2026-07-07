package com.tgac.logic.ckanren;

// ABOUTME: The shared cross-factor vocabulary: information a propagator or store has
// ABOUTME: inferred but may not apply itself — bindings grow, domains shrink.

import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;

/**
 * A message addressed to a factor of the {@link Package}, never to a store
 * (docs/design/capability-constraint-api.md §2.4). Exactly two kinds, matching the
 * two shared factors of the product lattice: {@link #bind} grows the substitution,
 * {@link #narrow} shrinks a domain. The reference to {@link Domain} is a deliberate
 * package cycle: the vocabulary couples to the system's shared domain concept
 * (rule B of the composition model), not to the FD store.
 */
public abstract class Inference {

	private Inference() {
	}

	/** Inferred bindings — the full new substitution map, per the chokepoint convention. */
	public static Inference bind(HashMap<LVar<?>, Term<?>> newSubstitutions) {
		return new Bind(newSubstitutions);
	}

	/**
	 * An inferred domain for {@code target}. Applied through {@code processDom}, so a
	 * ground target becomes a membership check, an unchanged domain is a no-op, a
	 * collapse binds through the chokepoint, and an emptied domain fails.
	 */
	public static Inference narrow(Term<?> target, Domain<?> domain) {
		return new Narrow(target, domain);
	}

	/**
	 * The step-1 applier: interpret this inference under the current protocol. The
	 * capability driver (step 2) will pattern-match instead, so it can dedup and
	 * detect contradictions before applying.
	 */
	public abstract Goal toGoal();

	private static final class Bind extends Inference {
		private final HashMap<LVar<?>, Term<?>> newSubstitutions;

		private Bind(HashMap<LVar<?>, Term<?>> newSubstitutions) {
			this.newSubstitutions = newSubstitutions;
		}

		@Override
		public Goal toGoal() {
			return s -> StoreSupport.processPrefix(newSubstitutions).apply(s);
		}

		@Override
		public String toString() {
			return "bind" + newSubstitutions;
		}
	}

	private static final class Narrow extends Inference {
		private final Term<?> target;
		private final Domain<?> domain;

		private Narrow(Term<?> target, Domain<?> domain) {
			this.target = target;
			this.domain = domain;
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public Goal toGoal() {
			// walk at APPLICATION time: the target may have been bound meanwhile (by an
			// earlier inference of the same verdict, or captured pre-walk by the emitter);
			// narrowing a stale var object would re-bind a bound variable — exactly the
			// violation the chokepoint's full-map contract forbids
			return s -> ((Domain) domain)
					.processDom(com.tgac.logic.unification.MiniKanren.walk(s, (Term) target))
					.apply(s);
		}

		@Override
		public String toString() {
			return target + " ⊂ " + domain;
		}
	}
}
