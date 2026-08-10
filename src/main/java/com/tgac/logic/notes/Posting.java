package com.tgac.logic.notes;

// ABOUTME: One atomic constraint posting — the chokepoint's statement vocabulary
// ABOUTME: as a value: a unification literal or a stored-item statement. Closed.

import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * What knowledge may enter a package, as data: a binding resolves or an item
 * is stated. A posting's imposition routes the chokepoint like any statement.
 * The constructors are the whole vocabulary — a program is not a posting, so
 * negation of postings never becomes negation of programs.
 */
public interface Posting {

	/** The posting as its statement goal — imposition routes the chokepoint. */
	Goal impose();

	/** Every term this posting speaks about — the declared surface. */
	Stream<Term<?>> terms();

	/** {@code lhs = rhs}: a unification literal, Prefix-shaped at imposition time. */
	static <T> Posting bind(Unifiable<T> lhs, Unifiable<T> rhs) {
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
	static Posting state(List<Term<?>> actuals, Function<List<Term<?>>, Stored> maker) {
		return new Statement(actuals, maker);
	}

	/**
	 * A whole factor as a call-value — the same (actuals, template) shape
	 * with an {@link Absorbable} product, imposed through the bulk statement
	 * entry, which registers the resident store itself: no residence guard
	 * needed. How a domain membership posts: the factor IS the knowledge.
	 */
	static Posting absorb(List<Term<?>> actuals, Function<List<Term<?>>, Absorbable<?>> maker) {
		return new Absorption(actuals, maker);
	}

	@Value
	class Binding<T> implements Posting {
		Unifiable<T> lhs;
		Unifiable<T> rhs;

		@Override
		public Goal impose() {
			return Constraints.unify(lhs, rhs);
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
	class Statement implements Posting {
		private final List<Term<?>> actuals;
		private final Function<List<Term<?>>, Stored> maker;
		private final Stored item;

		private Statement(List<Term<?>> actuals, Function<List<Term<?>>, Stored> maker) {
			this.actuals = actuals;
			this.maker = maker;
			this.item = maker.apply(actuals);
		}

		@Override
		public Goal impose() {
			return Propagation.activate(item).and(landed());
		}

		/**
		 * Package.withStored silently no-ops on an unregistered store, and a
		 * dropped statement would read "unchanged" — the false cross-off
		 * direction, which can veto a satisfiable branch. Residence is
		 * asserted after posting.
		 */
		private Goal landed() {
			return s -> {
				if (!s.getStores().containsKey(item.getStoreClass())) {
					throw new IllegalStateException(
							"statement posting dropped: no store registered for "
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
	 * same identity discipline as {@link Statement}.
	 */
	@Getter
	@EqualsAndHashCode(of = "factor")
	class Absorption implements Posting {
		private final List<Term<?>> actuals;
		private final Function<List<Term<?>>, Absorbable<?>> maker;
		private final Absorbable<?> factor;

		private Absorption(List<Term<?>> actuals, Function<List<Term<?>>, Absorbable<?>> maker) {
			this.actuals = actuals;
			this.maker = maker;
			this.factor = maker.apply(actuals);
		}

		@Override
		public Goal impose() {
			return Propagation.absorb(factor);
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
