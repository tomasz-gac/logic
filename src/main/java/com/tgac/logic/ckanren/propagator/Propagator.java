package com.tgac.logic.ckanren.propagator;

// ABOUTME: A parked constraint body that reports a Verdict — the framework owns the
// ABOUTME: parked lifecycle; watch matching resolves against the live state.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

/**
 * The parked unit of the wake machinery (docs/design/capability-constraint-api.md
 * §2.2). Extends {@link Stored} so park/remove route to the owning store without a
 * wrapper. Watch matching walks the watched terms against the LIVE state, so
 * aliasing (x bound to y) re-targets the watch structurally, where the old
 * Constraint protocol relied on the re-park-with-freshly-walked-args side effect of
 * remove-and-rerun.
 */
public interface Propagator extends Stored {

	/** The terms whose variables this propagator watches — as stated, un-walked. */
	Iterable<? extends Term<?>> watchedTerms();

	/** Re-examine against the current state. Reads anything, mutates nothing. */
	Verdict propagate(Package state);

	/**
	 * Does a change to {@code changed} re-run this propagator? {@code changed} is a
	 * newly bound variable during a wake, or an answer term during enforcement
	 * (whose components match element-wise, as the old relevance check did).
	 */
	default boolean watches(Package state, Term<?> changed) {
		for (Term<?> watchedTerm : watchedTerms()) {
			// follow the whole walk chain: the changed variable may be the watched
			// term itself, an alias link in the middle, or the end of the chain —
			// a full walk would step THROUGH a just-bound variable to its value
			// and miss the match
			Term<?> cur = watchedTerm;
			while (true) {
				if (cur.equals(changed)) {
					return true;
				}
				if (changed.isVal() && changedContains(changed, cur)) {
					return true;
				}
				if (!cur.asVar().isDefined()) {
					break;
				}
				Term<?> next = state.getSubstitutions().getOrElse(cur.asVar().get(), null);
				if (next == null) {
					break;
				}
				cur = next;
			}
		}
		return false;
	}

	/** Mirrors the old anyRelevantVar: a ground composite matches element-wise. */
	static boolean changedContains(Term<?> changed, Term<?> live) {
		Object w = changed.get();
		return MiniKanren.asIterable(w)
				.orElse(() -> MiniKanren.tupleAsIterable(w))
				.map(it -> StreamSupport.stream(it.spliterator(), false)
						.map(MiniKanren::wrapTerm)
						.anyMatch(live::equals))
				.getOrElse(() -> MiniKanren.asLList(changed)
						.map(l -> l.stream()
								.anyMatch(e -> e.fold(live::equals, live::equals)))
						.getOrElse(false));
	}

	/** A propagator from its owning store, watched terms and body. */
	static Propagator of(
			Class<? extends Store> storeClass,
			Iterable<? extends Term<?>> watchedTerms,
			Function<Package, Verdict> body) {
		return new Propagator() {
			@Override
			public Iterable<? extends Term<?>> watchedTerms() {
				return watchedTerms;
			}

			@Override
			public Verdict propagate(Package state) {
				return body.apply(state);
			}

			@Override
			public Class<? extends Store> getStoreClass() {
				return storeClass;
			}

			@Override
			public String toString() {
				return "propagator" + watchedTerms;
			}
		};
	}

	/**
	 * The closed set of propagation outcomes (see
	 * docs/design/capability-constraint-api.md §2.2). {@code keep} is the default-safe
	 * case: a propagator that cannot decide stays parked, so forgetting to re-park —
	 * the classic silent-evaporation trap of the goal-based protocol — cannot be
	 * expressed. Java 8 has no sealed types; the set is closed by the private
	 * constructor.
	 */
	public abstract class Verdict {

		private Verdict() {
		}

		/** The constraint is violated — the branch dies. */
		public static Verdict fail() {
			return Fail.INSTANCE;
		}

		/** Undecided — stay parked and wake me again on change. */
		public static Verdict keep() {
			return Keep.INSTANCE;
		}

		/** Entailed — can never be violated again (Gecode's ES_SUBSUMED); forget me. */
		public static Verdict subsumed() {
			return Subsumed.INSTANCE;
		}

		/** Stay parked AND apply what I inferred, in list order. */
		public static Verdict narrowed(List<Inference> inferences) {
			return new Narrowed(inferences);
		}

		/**
		 * Remove me AND splice this goal into the search — the suspension escape
		 * hatch (docs/design/suspensions.md §5). The goal may branch arbitrarily, so
		 * the driver must NOT run it mid-propagation: it is collected and spliced only
		 * after the pass quiesces.
		 */
		public static Verdict run(Goal goal) {
			return new Run(goal);
		}

		public abstract <R> R match(
				Supplier<R> onFail,
				Supplier<R> onKeep,
				Supplier<R> onSubsumed,
				Function<List<Inference>, R> onNarrowed,
				Function<Goal, R> onRun);

		private static final class Fail extends Verdict {
			static final Fail INSTANCE = new Fail();

			@Override
			public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
					Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
				return onFail.get();
			}

			@Override
			public String toString() {
				return "fail";
			}
		}

		private static final class Keep extends Verdict {
			static final Keep INSTANCE = new Keep();

			@Override
			public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
					Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
				return onKeep.get();
			}

			@Override
			public String toString() {
				return "keep";
			}
		}

		private static final class Subsumed extends Verdict {
			static final Subsumed INSTANCE = new Subsumed();

			@Override
			public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
					Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
				return onSubsumed.get();
			}

			@Override
			public String toString() {
				return "subsumed";
			}
		}

		private static final class Run extends Verdict {
			private final Goal goal;

			private Run(Goal goal) {
				this.goal = goal;
			}

			@Override
			public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
					Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
				return onRun.apply(goal);
			}

			@Override
			public String toString() {
				return "run(" + goal + ")";
			}
		}

		private static final class Narrowed extends Verdict {
			private final List<Inference> inferences;

			private Narrowed(List<Inference> inferences) {
				this.inferences = inferences;
			}

			@Override
			public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
					Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
				return onNarrowed.apply(inferences);
			}

			@Override
			public String toString() {
				return "narrowed" + inferences;
			}
		}
	}
}
