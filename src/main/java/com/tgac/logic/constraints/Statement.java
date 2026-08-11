package com.tgac.logic.constraints;

// ABOUTME: The chokepoint's statement vocabulary lifted to Goal: apply IS the
// ABOUTME: imposition — a binding, a stated item, or an absorbed factor. Closed.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.NamedGoal;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.goals.optimizer.Optimizer;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * What knowledge may enter a package, as a GOAL: applying a statement imposes
 * it through the chokepoint, so the same value is a conjunct in a program and
 * a literal in a nogood. The constructors are the whole vocabulary — one per
 * chokepoint door, holding the imposed content DIRECTLY (the item a store
 * would post, the factor it would meet) — so a store's front door returns the
 * statement and no adapter layer exists. A program is not a statement:
 * negation of statements never becomes negation of programs. Implementing
 * this interface is claiming the imposition law (idempotent, monotone, at
 * most one success, chokepoint-only); the laws kit checks constructors, not
 * calls.
 *
 * <p>The 0-or-1 taxonomy lands here as {@link Bounded}: a statement succeeds
 * at most once, so its order is never computed — it is 1 by construction,
 * with {@link #doomed} as the optional eager 0 under partial knowledge
 * (failure found at pricing is failure forever — monotone).
 */
public interface Statement extends Goal, Bounded {

	/** Every term this statement speaks about — the declared surface. */
	Stream<Term<?>> terms();

	/**
	 * Provably failing under the current partial knowledge? A TRUST SURFACE
	 * like every bound: store lookups, never store trials, and never claim
	 * doom that later knowledge could lift.
	 */
	default boolean doomed(Package p) {
		return false;
	}

	@Override
	default long answers(Substitutions s) {
		return 1;
	}

	@Override
	default long answers(Package p) {
		return doomed(p) ? 0 : 1;
	}

	/** {@code lhs = rhs}: a unification statement — {@link Constraints#unify}. */
	static <T> Statement bind(Unifiable<T> lhs, Unifiable<T> rhs) {
		return Constraints.unify(lhs, rhs);
	}

	/** Naming preserves the statement face; the label stays outside identity. */
	@Override
	default Statement named(String name) {
		return named(p -> name);
	}

	@Override
	default Statement named(Function<Package, String> label) {
		return new Named(this, NamedGoal.of(label, this));
	}

	/**
	 * The conjunction of statements is a statement (each succeeds at most
	 * once, chokepoint-only — the class is closed under ∧); doomed when any
	 * part is.
	 */
	static Statement all(Statement... statements) {
		return new AllOf(List.of(statements), p -> false);
	}

	/** {@link #all} with a joint doom check the parts alone cannot see. */
	static Statement all(Predicate<Package> doomed, Statement... statements) {
		return new AllOf(List.of(statements), doomed);
	}

	/**
	 * The stated item held directly — equality is the item's own (lawful
	 * under the named-schema contract); registration and pricing are the
	 * owning store's business and stay outside identity.
	 */
	@Getter
	@EqualsAndHashCode(of = "item")
	class Activation implements Statement {
		private final Stored item;
		private final UnaryOperator<Package> registration;
		private final Predicate<Package> doomCheck;

		Activation(Stored item, UnaryOperator<Package> registration,
				Predicate<Package> doomCheck) {
			this.item = item;
			this.registration = registration;
			this.doomCheck = doomCheck;
		}

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return Propagation.activation(item).and(landed())
					.apply(registration.apply(pkg));
		}

		/**
		 * Package.withStored silently no-ops on an unregistered store, and a
		 * dropped statement would read "unchanged" — the false cross-off
		 * direction, which can veto a satisfiable branch. Residence is
		 * asserted after imposition.
		 */
		private Goal landed() {
			return s -> {
				if (!s.getStores().containsKey(item.getStoreClass())) {
					throw new IllegalStateException(
							"statement dropped: no store registered for "
									+ item.getStoreClass().getSimpleName() + " — " + item);
				}
				return Cont.just(s);
			};
		}

		@Override
		public boolean doomed(Package p) {
			return doomCheck.test(p);
		}

		@Override
		public Stream<Term<?>> terms() {
			return item.terms();
		}

		@Override
		public String toString() {
			return "state(" + item + ")";
		}
	}

	/** The absorbed factor held directly — equality is the factor's own. */
	@Getter
	@EqualsAndHashCode(of = "factor")
	class Absorption implements Statement {
		private final Absorbable<?> factor;
		private final List<Term<?>> declared;

		Absorption(Absorbable<?> factor, List<Term<?>> declared) {
			this.factor = factor;
			this.declared = declared;
		}

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return Propagation.absorption(factor).apply(pkg);
		}

		@Override
		public Stream<Term<?>> terms() {
			return declared.toJavaStream().map(t -> (Term<?>) t);
		}

		@Override
		public String toString() {
			return "absorb(" + factor + ")";
		}
	}

	/**
	 * A named statement: tracing rides the wrapped {@link NamedGoal}, the
	 * statement face delegates, and IDENTITY IS THE INNER STATEMENT'S — the
	 * label is presentation, not content, so nogood literal comparison and
	 * dedup see through it.
	 */
	@Getter
	@EqualsAndHashCode(of = "inner")
	class Named implements Statement {
		private final Statement inner;
		private final NamedGoal named;

		Named(Statement inner, NamedGoal named) {
			this.inner = inner;
			this.named = named;
		}

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return named.apply(pkg);
		}

		@Override
		public Fiber<Goal> accept(Optimizer optimizer) {
			return named.accept(optimizer);
		}

		@Override
		public Stream<Term<?>> terms() {
			return inner.terms();
		}

		@Override
		public boolean doomed(Package p) {
			return inner.doomed(p);
		}

		@Override
		public long answers(Substitutions s) {
			return inner.answers(s);
		}

		@Override
		public long answers(Package p) {
			return inner.answers(p);
		}

		@Override
		public String toString() {
			return named.toString();
		}
	}

	@Value
	class AllOf implements Statement {
		List<Statement> parts;
		Predicate<Package> jointDoom;

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return parts.foldLeft(Goal.success(), Goal::and).apply(pkg);
		}

		@Override
		public boolean doomed(Package p) {
			return jointDoom.test(p) || parts.exists(part -> part.doomed(p));
		}

		@Override
		public Stream<Term<?>> terms() {
			return parts.toJavaStream().flatMap(Statement::terms);
		}

		@Override
		public String toString() {
			return parts.mkString(" ∧ ");
		}
	}
}
