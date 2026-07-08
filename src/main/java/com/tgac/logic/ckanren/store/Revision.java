package com.tgac.logic.ckanren.store;

// ABOUTME: A store's revised self after a trigger — its own updated factor plus the
// ABOUTME: cross-store consequences (bindings, narrowed terms, runs); never a whole package.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Term;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The closed set of store revisions — AC-3's REVISE, returned as a value
 * (docs/design/minimal-constraint-vocabulary.md §2.2). A revision may only replace
 * the store's OWN factor; everything that crosses store boundaries rides the
 * payloads, expressed in the driver's two-word vocabulary: {@link Prefix}
 * (bindings grow) and narrowed {@link Term}s (re-examine watchers), plus run goals
 * for the post-quiescence splice. Touching the substitutions or another store's
 * entry is not expressible. Java 8 has no sealed types; the set is closed by the
 * private constructor.
 */
public abstract class Revision {

	private Revision() {
	}

	/** A record is violated — the branch dies. */
	public static Revision fail() {
		return Fail.INSTANCE;
	}

	/** Nothing to do. */
	public static Revision unchanged() {
		return Unchanged.INSTANCE;
	}

	/** Replace my factor; add consequences with the {@code with*} builders. */
	public static Updated updated(Store replacement) {
		return new Updated(replacement,
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	public abstract <R> R match(
			Supplier<R> onFail,
			Supplier<R> onUnchanged,
			Function<Updated, R> onUpdated);

	private static final class Fail extends Revision {
		static final Fail INSTANCE = new Fail();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				Function<Updated, R> onUpdated) {
			return onFail.get();
		}

		@Override
		public String toString() {
			return "fail";
		}
	}

	private static final class Unchanged extends Revision {
		static final Unchanged INSTANCE = new Unchanged();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				Function<Updated, R> onUpdated) {
			return onUnchanged.get();
		}

		@Override
		public String toString() {
			return "unchanged";
		}
	}

	public static final class Updated extends Revision {
		private final Store factor;
		private final List<Prefix> inferred;
		private final List<Term<?>> narrowed;
		private final List<Goal> runs;

		private Updated(Store factor,
				List<Prefix> inferred, List<Term<?>> narrowed, List<Goal> runs) {
			this.factor = factor;
			this.inferred = inferred;
			this.narrowed = narrowed;
			this.runs = runs;
		}

		/**
		 * An inferred binding delta. Each prefix is queued as its own Bind item so
		 * that contradictions between them surface through revalidation instead of
		 * being swallowed by a map merge.
		 */
		public Updated withInferred(Prefix prefix) {
			return new Updated(factor,
					appended(inferred, prefix), narrowed, runs);
		}

		/**
		 * A term whose watchers the OWNING store must still re-examine — an
		 * intra-store note consumed by the store's own cascade (e.g.
		 * {@code DomainUpdate.narrowAll} feeding the FD worklist). Never returned
		 * to the driver, which rejects it loudly: nothing outside the owner can
		 * act on a narrowing whose content lives in the owner's factor.
		 */
		public Updated withNarrowed(Term<?> x) {
			return new Updated(factor,
					inferred, appended(narrowed, x), runs);
		}

		/** A goal for the run lane, spliced after the drain quiesces. */
		public Updated withRun(Goal goal) {
			return new Updated(factor,
					inferred, narrowed, appended(runs, goal));
		}

		public Store factor() {
			return factor;
		}

		public List<Prefix> inferred() {
			return inferred;
		}

		public List<Term<?>> narrowed() {
			return narrowed;
		}

		public List<Goal> runs() {
			return runs;
		}

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				Function<Updated, R> onUpdated) {
			return onUpdated.apply(this);
		}

		@Override
		public String toString() {
			return "updated(" + factor
					+ (inferred.isEmpty() ? "" : ", bind" + inferred)
					+ (narrowed.isEmpty() ? "" : ", narrowed" + narrowed)
					+ (runs.isEmpty() ? "" : ", runs" + runs) + ")";
		}

		private static <T> List<T> appended(List<T> xs, T x) {
			List<T> result = new ArrayList<>(xs);
			result.add(x);
			return result;
		}
	}
}
