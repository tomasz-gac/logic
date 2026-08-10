package com.tgac.logic.constraints;

// ABOUTME: The chokepoint's statement vocabulary lifted to Goal: apply IS the
// ABOUTME: imposition — unification, stored-item statement, or absorbed factor. Closed.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * What knowledge may enter a package, as a GOAL: applying a statement imposes
 * it through the chokepoint, so the same value is a conjunct in a program and
 * a literal in a nogood. The constructors are the whole vocabulary —
 * a program is not a statement, so negation of statements never becomes
 * negation of programs. Implementing this interface is claiming the
 * imposition law (idempotent, monotone, at most one success, chokepoint-only);
 * the laws kit checks constructors, not calls.
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

	/** {@code lhs = rhs}: a unification statement, Prefix-shaped at imposition time. */
	static <T> Statement bind(Unifiable<T> lhs, Unifiable<T> rhs) {
		return new Binding<>(lhs, rhs);
	}

	/**
	 * A constraint statement as a call-value: arguments as terms plus the
	 * owning store's item maker — the (actuals, template) shape. The item is
	 * generated at construction; a renaming regenerates it at the renamed
	 * actuals, so transcription never needs the item's structure. The maker
	 * must read its variables through the actuals it is handed, never
	 * lexical capture; ground data may close over.
	 */
	static Statement state(List<Term<?>> actuals, Function<List<Term<?>>, Stored> maker) {
		return new Item(actuals, maker);
	}

	/**
	 * A whole factor as a call-value — the same (actuals, template) shape
	 * with an {@link Absorbable} product, imposed through the bulk statement
	 * entry, which registers the resident store itself: no residence guard
	 * needed. How a domain membership posts: the factor IS the knowledge.
	 */
	static Statement absorb(List<Term<?>> actuals, Function<List<Term<?>>, Absorbable<?>> maker) {
		return new Absorption(actuals, maker, p -> false);
	}

	/** {@link #absorb} with the owning store's doom check. */
	static Statement absorb(List<Term<?>> actuals, Function<List<Term<?>>, Absorbable<?>> maker,
			Predicate<Package> doomed) {
		return new Absorption(actuals, maker, doomed);
	}

	@Value
	class Binding<T> implements Statement {
		Unifiable<T> lhs;
		Unifiable<T> rhs;

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return Constraints.unify(lhs, rhs).apply(pkg);
		}

		@Override
		public Stream<Term<?>> terms() {
			return Stream.concat(
					MiniKanren.namesIn(lhs).map(name -> (Term<?>) name),
					MiniKanren.namesIn(rhs).map(name -> (Term<?>) name));
		}

		@Override
		public String toString() {
			return lhs + " = " + rhs;
		}
	}

	/**
	 * Equality delegates to the GENERATED item — lawful under the named-schema
	 * contract (a propagator compares as store, name and watched terms, body
	 * excluded) — so the maker is excluded from identity exactly as the
	 * contract prescribes, and equality at renamed actuals follows from the
	 * regenerated item.
	 */
	@Getter
	@EqualsAndHashCode(of = "item")
	class Item implements Statement {
		private final List<Term<?>> actuals;
		private final Function<List<Term<?>>, Stored> maker;
		private final Stored item;

		private Item(List<Term<?>> actuals, Function<List<Term<?>>, Stored> maker) {
			this.actuals = actuals;
			this.maker = maker;
			this.item = maker.apply(actuals);
		}

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return Propagation.activate(item).and(landed()).apply(pkg);
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
		public Stream<Term<?>> terms() {
			return actuals.toJavaStream().map(t -> (Term<?>) t);
		}

		@Override
		public String toString() {
			return "state(" + item + ")";
		}
	}

	/**
	 * Equality delegates to the generated factor, the maker excluded — the
	 * same identity discipline as {@link Item}. The doom check is the owning
	 * store's and stays outside identity: pricing is not what the statement
	 * says.
	 */
	@Getter
	@EqualsAndHashCode(of = "factor")
	class Absorption implements Statement {
		private final List<Term<?>> actuals;
		private final Function<List<Term<?>>, Absorbable<?>> maker;
		private final Absorbable<?> factor;
		private final Predicate<Package> doomCheck;

		private Absorption(List<Term<?>> actuals, Function<List<Term<?>>, Absorbable<?>> maker,
				Predicate<Package> doomCheck) {
			this.actuals = actuals;
			this.maker = maker;
			this.factor = maker.apply(actuals);
			this.doomCheck = doomCheck;
		}

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return Propagation.absorb(factor).apply(pkg);
		}

		@Override
		public boolean doomed(Package p) {
			return doomCheck.test(p);
		}

		@Override
		public Stream<Term<?>> terms() {
			return actuals.toJavaStream().map(t -> (Term<?>) t);
		}

		@Override
		public String toString() {
			return "absorb(" + factor + ")";
		}
	}
}
