package com.tgac.logic.nogoods;

// ABOUTME: The whole trial as ONE visitor: unification rows step fast at the
// ABOUTME: substitution level, store rows impose on the scratch, conjuncts thread.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Value;

/**
 * One owner for per-literal trial semantics. The UNIFICATION rows answer at
 * the substitution level — Neq's machinery under its real name: refuted and
 * entailed are exact (both monotone under binding growth, and entailment has
 * no veto surface left — the bindings already stand, so the imposition would
 * be a no-op resolve), the remainder is the residual prefix RESOLVED (a
 * literal simplifies into the prefix posting and shrinks monotonically), and
 * everything the substitutions cannot see errs toward OWED, the delay-safe
 * direction: a store veto the imposition would discharge on keeps the nogood
 * wider until the ground floor, and eager discharge, if earliness is ever
 * worth buying, belongs on the doomed(Package) seam — store lookups, never
 * store trials. These rows answer {@code Fiber.done}: eager Done-composition
 * keeps whole binding chains inside the current step.
 *
 * <p>The STORE rows impose on the scratch and classify by the change
 * reading, keeping their ORIGINAL selves when owed — nothing is read back
 * out of a factor. A CONJUNCT threads its parts through the growing scratch,
 * so a mixed nogood crosses off its decided parts at part granularity.
 */
final class Trial implements Posting.Visitor<Fiber<Trial.Outcome>> {

	private final Package scratch;

	Trial(Package scratch) {
		this.scratch = scratch;
	}

	static Fiber<Outcome> trial(Posting forbidden, Package scratch) {
		return forbidden.accept(new Trial(scratch));
	}

	/** Refuted: {@code remainder == null} and not entailed. */
	@Value
	static class Outcome {
		Posting remainder;
		Package grown;
		boolean entailed;

		static Outcome refuted() {
			return new Outcome(null, null, false);
		}

		static Outcome entailed(Package grown) {
			return new Outcome(null, grown, true);
		}

		static Outcome owed(Posting remainder, Package grown) {
			return new Outcome(remainder, grown, false);
		}

		boolean isRefuted() {
			return remainder == null && !entailed;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Fiber<Outcome> visit(UnifyGoal<?> unification) {
		UnifyGoal<Object> bind = (UnifyGoal<Object>) unification;
		Option<Prefix> minted = (bind.isNoCheck() ?
				MiniKanren.unifyPrefixUnsafe(scratch.substitution(), bind.getU(), bind.getV()) :
				MiniKanren.unifyPrefix(scratch.substitution(), bind.getU(), bind.getV()))
				.get();
		// the equality can NEVER hold: unification failure is monotone under
		// binding growth (a structural clash stays a clash in every extension
		// of these substitutions), so the forbidden conjunction is refuted
		// FOREVER, not just for this state — the nogood discharges
		if (!minted.isDefined()) {
			return Fiber.done(Outcome.refuted());
		}
		// an EMPTY residual is not the trial passing — the literal is
		// entailed and crosses off; the branch-failing verdict (no survivors
		// left = violated) is the caller's fold
		return Fiber.done(resolved(minted.get(), scratch.substitution()));
	}

	/**
	 * RE-UNIFICATION per pair, not the agenda's equality trichotomy
	 * ({@code Prefix.revalidate} reads bound-vs-open as contradiction — right
	 * for a Bind item, fatally wrong here: the trial asks whether the
	 * EQUALITY can still hold, Disequality's own reading of the same pairs).
	 */
	@Override
	public Fiber<Outcome> visit(Posting.Resolution resolution) {
		Substitutions current = scratch.substitution();
		List<Posting> residuals = List.empty();
		for (Tuple2<com.tgac.logic.unification.LVar<?>, Term<?>> pair : resolution.getPrefix().bindings()) {
			@SuppressWarnings("unchecked")
			Option<Prefix> minted = MiniKanren.unifyPrefix(current,
					(Term<Object>) pair._1, (Term<Object>) pair._2).get();
			if (!minted.isDefined()) {
				return Fiber.done(Outcome.refuted());
			}
			Prefix residual = minted.get();
			if (!residual.isEmpty()) {
				residuals = residuals.append(Propagation.resolve(residual));
				current = residual.appliedTo(current);
			}
		}
		if (residuals.isEmpty()) {
			return Fiber.done(Outcome.entailed(withSubstitutions(current)));
		}
		return Fiber.done(Outcome.owed(
				residuals.size() == 1 ?
						residuals.head() :
						Posting.all(residuals.toJavaArray(Posting[]::new)),
				withSubstitutions(current)));
	}

	private Outcome resolved(Prefix residual, Substitutions subs) {
		return residual.isEmpty() ?
				Outcome.entailed(scratch) :
				Outcome.owed(Propagation.resolve(residual),
						withSubstitutions(residual.appliedTo(subs)));
	}

	/**
	 * Store factors deliberately do NOT hear these bindings: staleness only
	 * shifts verdicts toward "owed", the delay-safe direction.
	 */
	private Package withSubstitutions(Substitutions grown) {
		return Package.of(grown, scratch.getStores());
	}

	@Override
	public Fiber<Outcome> visit(Posting.Activation activation) {
		return imposed(activation);
	}

	@Override
	public Fiber<Outcome> visit(Posting.Absorption absorption) {
		return imposed(absorption);
	}

	/**
	 * The imposition read three ways: FAILS = refuted; changes NOTHING =
	 * entailed (lawful by monotonicity); NEW knowledge = owed, the literal
	 * surviving as its ORIGINAL self. A fork reads conservatively — owed,
	 * with the scratch unthreaded (missed jointness only ever keeps more).
	 */
	private Fiber<Outcome> imposed(Posting literal) {
		return Verification.imposed(literal, scratch).map(worlds -> {
			if (worlds.isEmpty()) {
				return Outcome.refuted();
			}
			if (worlds.size() > 1) {
				return Outcome.owed(literal, scratch);
			}
			Package grown = worlds.head();
			return Verification.unchanged(scratch, grown) ?
					Outcome.entailed(grown) :
					Outcome.owed(literal, grown);
		});
	}

	/** Parts thread through the growing scratch; one refusal refutes the whole. */
	@Override
	public Fiber<Outcome> visit(Posting.AllOf all) {
		return parts(all.getParts(), List.empty(), scratch);
	}

	private static Fiber<Outcome> parts(List<Posting> pending, List<Posting> remainders, Package current) {
		if (pending.isEmpty()) {
			return Fiber.done(remainders.isEmpty() ?
					Outcome.entailed(current) :
					Outcome.owed(remainders.size() == 1 ?
							remainders.head() :
							Posting.all(remainders.toJavaArray(Posting[]::new)),
							current));
		}
		return trial(pending.head(), current).flatMap(outcome -> {
			if (outcome.isRefuted()) {
				return Fiber.done(Outcome.refuted());
			}
			return parts(pending.tail(),
					outcome.isEntailed() ? remainders : remainders.append(outcome.getRemainder()),
					outcome.getGrown());
		});
	}
}
