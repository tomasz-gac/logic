package com.tgac.logic.constraints;

// ABOUTME: Knowledge injection as a Goal — the chokepoint's posting vocabulary:
// ABOUTME: apply IS the imposition; a binding, a stated item, or an absorbed factor.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Transcribable;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.NamedGoal;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.goals.optimizer.Optimizer;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * What knowledge may enter a package, as a GOAL: applying a posting imposes
 * it through the chokepoint, so the same value is a conjunct in a program and
 * a literal in a nogood. The constructors are the whole vocabulary — one per
 * chokepoint door, holding the imposed content DIRECTLY (the item a store
 * would post, the factor it would meet) — so a store's front door returns the
 * posting and no adapter layer exists. A program is not a posting:
 * negation of postings never becomes negation of programs. Implementing
 * this interface is claiming the imposition law (idempotent, monotone, at
 * most one success, chokepoint-only); the laws kit checks constructors, not
 * calls.
 *
 * <p>The 0-or-1 taxonomy lands here as {@link Bounded}: a posting succeeds
 * at most once, so its order is never computed — it is 1 by construction,
 * with {@link #doomed} as the optional eager 0 under partial knowledge
 * (failure found at pricing is failure forever — monotone).
 */
public interface Posting extends Goal, Bounded {

	/** Every term this posting speaks about — the declared surface. */
	Stream<Term<?>> terms();

	/**
	 * This posting under changed names — the crossing keeps every row WRAPPED
	 * (nogood-store.md §7): terms rename through the {@link Renaming}, ground
	 * data rides unchanged, items re-instantiate over the renamed terms
	 * ({@link Transcribable}). Labels are presentation and drop; doom checks
	 * capture lexical terms and reset to the safe default; registrations are
	 * store-generic and carry. Content that cannot transcribe refuses loudly
	 * with its name — the boundary never crosses knowledge silently.
	 */
	Fiber<Posting> rename(Renaming renaming);

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

	/** {@code lhs = rhs}: a unification posting — {@link Constraints#unify}. */
	static <T> Posting bind(Unifiable<T> lhs, Unifiable<T> rhs) {
		return Constraints.unify(lhs, rhs);
	}

	/** Naming preserves the posting face; the label stays outside identity. */
	@Override
	default Posting named(String name) {
		return named(p -> name);
	}

	@Override
	default Posting named(Function<Package, String> label) {
		return new Named(this, NamedGoal.of(label, this));
	}

	/**
	 * The conjunction of postings is a posting (each succeeds at most
	 * once, chokepoint-only — the class is closed under ∧); doomed when any
	 * part is.
	 */
	static Posting all(Posting... statements) {
		return new AllOf(List.of(statements), p -> false);
	}

	/** {@link #all} with a joint doom check the parts alone cannot see. */
	static Posting all(Predicate<Package> doomed, Posting... statements) {
		return new AllOf(List.of(statements), doomed);
	}

	/**
	 * The stated item held directly — equality is the item's own (lawful
	 * under the named-schema contract); registration and pricing are the
	 * owning store's business and stay outside identity.
	 */
	@Getter
	@EqualsAndHashCode(of = "item")
	class Activation implements Posting {
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
		public Fiber<Posting> rename(Renaming renaming) {
			if (!(item instanceof Transcribable)) {
				throw new IllegalStateException(
						"stated item cannot cross the boundary: " + item);
			}
			return ((Transcribable) item).rename(renaming)
					.map(renamed -> Propagation.activate(renamed, registration, p -> false));
		}

		@Override
		public String toString() {
			return "state(" + item + ")";
		}
	}

	/**
	 * A resolved prefix held directly — the bulk binding load through the
	 * chokepoint ({@code UnifyGoal} is its single-unification face: mint the
	 * prefix, resolve it). Equality is the prefix's own.
	 */
	@Getter
	@EqualsAndHashCode(of = "prefix")
	class Resolution implements Posting {
		private final Prefix prefix;

		Resolution(Prefix prefix) {
			this.prefix = prefix;
		}

		@Override
		public Cont<Package, Nothing> apply(Package pkg) {
			return Propagation.resolution(prefix).apply(pkg);
		}

		@Override
		public Stream<Term<?>> terms() {
			return StreamSupport.stream(prefix.bindings().spliterator(), false)
					.flatMap(binding -> Stream.concat(
							Stream.of((Term<?>) binding._1),
							MiniKanren.namesIn(binding._2).map(name -> (Term<?>) name)));
		}

		/**
		 * A prefix is bindings, so it crosses as the conjunction of its
		 * binds — each pair re-keyed; the checked mint is lineage-local and
		 * unification is its portable spelling, re-imposed through the
		 * unifier on arrival.
		 */
		@Override
		@SuppressWarnings("unchecked")
		public Fiber<Posting> rename(Renaming renaming) {
			return List.ofAll(prefix.bindings()).foldLeft(
							Fiber.<List<Posting>> done(List.empty()),
							(acc, binding) -> acc.flatMap(binds ->
									renaming.apply((Term<?>) binding._1)
											.flatMap(lhs -> renaming.apply(binding._2)
													.map(rhs -> binds.append(UnifyGoal.of(
															(Term<Object>) lhs, (Term<Object>) rhs, false))))))
					.map(binds -> binds.size() == 1
							? binds.head()
							: Posting.all(binds.toJavaArray(Posting[]::new)));
		}

		@Override
		public String toString() {
			return prefix.toString();
		}
	}

	/** The absorbed factor held directly — equality is the factor's own. */
	@Getter
	@EqualsAndHashCode(of = "factor")
	class Absorption implements Posting {
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
		public Fiber<Posting> rename(Renaming renaming) {
			if (!(factor instanceof Projectable)) {
				throw new IllegalStateException(
						"absorbed factor cannot cross the boundary: " + factor);
			}
			return ((Projectable<?>) factor).rename(renaming)
					.flatMap(renamed -> declared.foldLeft(
									Fiber.<List<Term<?>>> done(List.empty()),
									(acc, term) -> acc.flatMap(terms ->
											renaming.apply(term).map(terms::append)))
							.map(terms -> Propagation.absorb(renamed, terms)));
		}

		@Override
		public String toString() {
			return "absorb(" + factor + ")";
		}
	}

	/**
	 * A named posting: tracing rides the wrapped {@link NamedGoal}, the
	 * posting face delegates, and IDENTITY IS THE INNER STATEMENT'S — the
	 * label is presentation, not content, so nogood literal comparison and
	 * dedup see through it.
	 */
	@Getter
	@EqualsAndHashCode(of = "inner")
	class Named implements Posting {
		private final Posting inner;
		private final NamedGoal named;

		Named(Posting inner, NamedGoal named) {
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

		/** Labels are presentation; canonical data crosses bare. */
		@Override
		public Fiber<Posting> rename(Renaming renaming) {
			return inner.rename(renaming);
		}

		@Override
		public String toString() {
			return named.toString();
		}
	}

	@Value
	class AllOf implements Posting {
		List<Posting> parts;
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
			return parts.toJavaStream().flatMap(Posting::terms);
		}

		/** Parts transcribe wrapped; the joint doom resets to the safe default. */
		@Override
		public Fiber<Posting> rename(Renaming renaming) {
			return parts.foldLeft(
							Fiber.<List<Posting>> done(List.empty()),
							(acc, part) -> acc.flatMap(renamed ->
									part.rename(renaming).map(renamed::append)))
					.map(renamed -> Posting.all(renamed.toJavaArray(Posting[]::new)));
		}

		@Override
		public String toString() {
			return parts.mkString(" ∧ ");
		}
	}
}
