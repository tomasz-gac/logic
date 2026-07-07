package com.tgac.logic.ckanren;

// ABOUTME: The outcome a propagator reports after re-examining its constraint — the
// ABOUTME: framework administers the parked lifecycle; bodies only ever report.

import com.tgac.logic.goals.Goal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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

	/** Permanently satisfied — forget me. */
	public static Verdict discharge() {
		return Discharge.INSTANCE;
	}

	/** Stay parked AND apply what I inferred, in list order. */
	public static Verdict narrowed(List<Inference> inferences) {
		return new Narrowed(inferences);
	}

	/**
	 * Discharge me AND splice this goal into the search — the suspension escape
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
			Supplier<R> onDischarge,
			Function<List<Inference>, R> onNarrowed,
			Function<Goal, R> onRun);

	private static final class Fail extends Verdict {
		static final Fail INSTANCE = new Fail();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onDischarge,
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
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onDischarge,
				Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
			return onKeep.get();
		}

		@Override
		public String toString() {
			return "keep";
		}
	}

	private static final class Discharge extends Verdict {
		static final Discharge INSTANCE = new Discharge();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onDischarge,
				Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
			return onDischarge.get();
		}

		@Override
		public String toString() {
			return "discharge";
		}
	}

	private static final class Run extends Verdict {
		private final Goal goal;

		private Run(Goal goal) {
			this.goal = goal;
		}

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onDischarge,
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
		public <R> R match(Supplier<R> onFail, Supplier<R> onKeep, Supplier<R> onDischarge,
				Function<List<Inference>, R> onNarrowed, Function<Goal, R> onRun) {
			return onNarrowed.apply(inferences);
		}

		@Override
		public String toString() {
			return "narrowed" + inferences;
		}
	}
}
