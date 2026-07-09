package com.tgac.logic.ckanren.store;

// ABOUTME: A store's revised self after a trigger — its own updated factor plus the
// ABOUTME: cross-store consequences (bindings, suspensions); never a whole package.

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
				Collections.emptyList(), Collections.emptyList());
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
		private final List<Suspension> suspensions;

		private Updated(Store factor, List<Prefix> inferred, List<Suspension> suspensions) {
			this.factor = factor;
			this.inferred = inferred;
			this.suspensions = suspensions;
		}

		/**
		 * An inferred binding delta. Each prefix is queued as its own Bind item so
		 * that contradictions between them surface through revalidation instead of
		 * being swallowed by a map merge.
		 */
		public Updated withInferred(Prefix prefix) {
			return new Updated(factor,
					appended(inferred, prefix), suspensions);
		}

		/**
		 * A search effect: parked until ripe, run-lane immediately when already
		 * ripe. The degenerate always-ripe form is a plain run.
		 */
		public Updated withSuspend(Suspension suspension) {
			return new Updated(factor, inferred, appended(suspensions, suspension));
		}

		public Store factor() {
			return factor;
		}

		public List<Prefix> inferred() {
			return inferred;
		}

		public List<Suspension> suspensions() {
			return suspensions;
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
					+ (suspensions.isEmpty() ? "" : ", " + suspensions) + ")";
		}

		private static <T> List<T> appended(List<T> xs, T x) {
			List<T> result = new ArrayList<>(xs);
			result.add(x);
			return result;
		}
	}
}
