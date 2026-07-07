package com.tgac.logic.ckanren.store;

// ABOUTME: A store's revised self after newly applied bindings — its own updated factor
// ABOUTME: plus inferences for the chokepoint to route; never a whole package.

import com.tgac.logic.ckanren.propagator.Inference;
import com.tgac.logic.unification.Store;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The closed set of store revisions — AC-3's REVISE, returned as a value
 * (docs/design/capability-constraint-api.md §2.3). A revision may only replace the
 * store's OWN factor and emit {@link Inference}s —
 * touching the substitutions or another store's entry is not expressible. Java 8 has
 * no sealed types; the set is closed by the private constructor.
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

	/** Replace my factor. */
	public static Revision updated(Store replacement) {
		return new Updated(replacement, Collections.emptyList());
	}

	/** Replace my factor AND route these inferences. */
	public static Revision updated(Store replacement, List<Inference> inferences) {
		return new Updated(replacement, inferences);
	}

	public abstract <R> R match(
			Supplier<R> onFail,
			Supplier<R> onUnchanged,
			BiFunction<Store, List<Inference>, R> onUpdated);

	private static final class Fail extends Revision {
		static final Fail INSTANCE = new Fail();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				BiFunction<Store, List<Inference>, R> onUpdated) {
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
				BiFunction<Store, List<Inference>, R> onUpdated) {
			return onUnchanged.get();
		}

		@Override
		public String toString() {
			return "unchanged";
		}
	}

	private static final class Updated extends Revision {
		private final Store replacement;
		private final List<Inference> inferences;

		private Updated(Store replacement, List<Inference> inferences) {
			this.replacement = replacement;
			this.inferences = inferences;
		}

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				BiFunction<Store, List<Inference>, R> onUpdated) {
			return onUpdated.apply(replacement, inferences);
		}

		@Override
		public String toString() {
			return "updated(" + replacement + ", " + inferences + ")";
		}
	}
}
