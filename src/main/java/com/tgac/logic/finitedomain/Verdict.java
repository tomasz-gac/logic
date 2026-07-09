package com.tgac.logic.finitedomain;

// ABOUTME: The outcome a propagator reports after re-examining its constraint — the
// ABOUTME: framework administers the parked lifecycle; bodies only ever report.

import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Store;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The closed set of propagation outcomes (see
 * docs/design/constraint-kernel.md). {@code keep} is the default-safe
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

	/**
	 * Stay parked AND apply this update to my OWN store's factor. Administered by
	 * the owning store, never the driver: the function receives the live state and
	 * the store's current factor and answers an {@link Update} — which cannot
	 * express touching anyone else's factor, and whose re-examination notes
	 * never leave the store.
	 */
	public static Verdict update(BiFunction<Package, Store, Update> f) {
		return new UpdateCase(f);
	}

	public abstract <R> R match(
			Supplier<R> onFail,
			Supplier<R> onKeep,
			Supplier<R> onSubsumed,
			Function<BiFunction<Package, Store, Update>, R> onUpdate);

	private static final class Fail extends Verdict {
		static final Fail INSTANCE = new Fail();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
				Function<BiFunction<Package, Store, Update>, R> onUpdate) {
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
				Function<BiFunction<Package, Store, Update>, R> onUpdate) {
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
				Function<BiFunction<Package, Store, Update>, R> onUpdate) {
			return onSubsumed.get();
		}

		@Override
		public String toString() {
			return "subsumed";
		}
	}

	private static final class UpdateCase extends Verdict {
		private final BiFunction<Package, Store, Update> f;

		private UpdateCase(BiFunction<Package, Store, Update> f) {
			this.f = f;
		}

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onSubsumed,
				Function<BiFunction<Package, Store, Update>, R> onUpdate) {
			return onUpdate.apply(f);
		}

		@Override
		public String toString() {
			return "update";
		}
	}
}
