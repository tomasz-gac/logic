package com.tgac.logic.notes;

// ABOUTME: One atomic constraint posting — the chokepoint's statement vocabulary
// ABOUTME: as a value: a unification literal or a stored-item statement. Closed.

import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.stream.Stream;
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

	/** A constraint statement — a {@link Stored} item, built by its owning store's front door. */
	static Posting state(Stored item) {
		return new Statement(item);
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

	@Value
	class Statement implements Posting {
		Stored item;

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
			return item.terms();
		}

		@Override
		public String toString() {
			return "state(" + item + ")";
		}
	}
}
