package com.tgac.logic.finitedomain;

// ABOUTME: A propagator's application to its OWN store's factor — the intra-store
// ABOUTME: answer type, distinct from the store→driver Revision.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.goals.Store;
import com.tgac.logic.unification.Term;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a {@code Verdict.update} function answers its administering store: the
 * updated factor, inferred bindings, run goals, and — the part that never leaves
 * the store — the terms whose watchers the store's own cascade must re-examine.
 * Keeping re-examination here (and not on {@code Revision}) makes leaking an
 * intra-store note to the driver unrepresentable.
 */
public abstract class Update {

	private Update() {
	}

	public static Update fail() {
		return Fail.INSTANCE;
	}

	public static Update unchanged() {
		return Unchanged.INSTANCE;
	}

	public static Applied applied(Store factor) {
		return new Applied(factor,
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	public abstract <R> R match(
			Supplier<R> onFail,
			Supplier<R> onUnchanged,
			Function<Applied, R> onApplied);

	private static final class Fail extends Update {
		static final Fail INSTANCE = new Fail();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				Function<Applied, R> onApplied) {
			return onFail.get();
		}

		@Override
		public String toString() {
			return "fail";
		}
	}

	private static final class Unchanged extends Update {
		static final Unchanged INSTANCE = new Unchanged();

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				Function<Applied, R> onApplied) {
			return onUnchanged.get();
		}

		@Override
		public String toString() {
			return "unchanged";
		}
	}

	public static final class Applied extends Update {
		private final Store factor;
		private final List<Prefix> inferred;
		private final List<Term<?>> reexamine;
		private final List<Goal> runs;

		private Applied(Store factor, List<Prefix> inferred,
				List<Term<?>> reexamine, List<Goal> runs) {
			this.factor = factor;
			this.inferred = inferred;
			this.reexamine = reexamine;
			this.runs = runs;
		}

		/** An inferred binding delta — routed to the driver via the store's Revision. */
		public Applied withInferred(Prefix prefix) {
			return new Applied(factor, appended(inferred, prefix), reexamine, runs);
		}

		/** A term the owning store's cascade must re-examine. Never leaves the store. */
		public Applied withReexamine(Term<?> x) {
			return new Applied(factor, inferred, appended(reexamine, x), runs);
		}

		/** A goal for the run lane — routed to the driver via the store's Revision. */
		public Applied withRun(Goal goal) {
			return new Applied(factor, inferred, reexamine, appended(runs, goal));
		}

		public Store factor() {
			return factor;
		}

		public List<Prefix> inferred() {
			return inferred;
		}

		public List<Term<?>> reexamine() {
			return reexamine;
		}

		public List<Goal> runs() {
			return runs;
		}

		@Override
		public <R> R match(Supplier<R> onFail, Supplier<R> onUnchanged,
				Function<Applied, R> onApplied) {
			return onApplied.apply(this);
		}

		@Override
		public String toString() {
			return "applied(" + factor
					+ (inferred.isEmpty() ? "" : ", bind" + inferred)
					+ (reexamine.isEmpty() ? "" : ", reexamine" + reexamine)
					+ (runs.isEmpty() ? "" : ", runs" + runs) + ")";
		}

		private static <T> List<T> appended(List<T> xs, T x) {
			List<T> result = new ArrayList<>(xs);
			result.add(x);
			return result;
		}
	}
}
