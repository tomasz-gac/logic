package com.tgac.logic.nogoods;

// ABOUTME: Neq's trial machinery re-homed: a literal's substitution-level reading —
// ABOUTME: refuted, entailed, or owed with the prefix as its simplified remainder.

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Value;

/**
 * The fast trial: literals that are pure binding claims answer verification's
 * three-way question with one trial unification against the scratch's
 * SUBSTITUTIONS — no package copy, no workforce claim. Exact on both hard
 * verdicts (unification failure and entailment are monotone under binding
 * growth, so neither can be lifted by pending agenda items or store
 * reactions); everything it cannot see errs toward OWED, the delay-safe
 * direction. The remainder is the revalidated delta itself, resolved — a
 * literal simplifies into the prefix posting and shrinks monotonically
 * across re-verifications (Neq's record behavior under its real name).
 * Literals holding store content answer {@code none}: the package trial
 * remains their reading.
 */
final class SubstitutionTrial implements Posting.Visitor<Option<SubstitutionTrial.Outcome>> {

	private final Substitutions subs;

	SubstitutionTrial(Substitutions subs) {
		this.subs = subs;
	}

	static Option<Outcome> step(Posting literal, Substitutions subs) {
		return literal.accept(new SubstitutionTrial(subs));
	}

	/** Refuted: {@code remainder == null}. Entailed: empty remainder prefix. */
	@Value
	static class Outcome {
		Posting remainder;
		Substitutions grown;
		boolean entailed;

		static Outcome refuted() {
			return new Outcome(null, null, false);
		}

		static Outcome entailed(Substitutions subs) {
			return new Outcome(null, subs, true);
		}

		static Outcome owed(Posting remainder, Substitutions grown) {
			return new Outcome(remainder, grown, false);
		}

		boolean isRefuted() {
			return remainder == null && !entailed;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Option<Outcome> visit(UnifyGoal<?> unification) {
		UnifyGoal<Object> bind = (UnifyGoal<Object>) unification;
		Option<Prefix> minted = (bind.isNoCheck() ?
				MiniKanren.unifyPrefixUnsafe(subs, bind.getU(), bind.getV()) :
				MiniKanren.unifyPrefix(subs, bind.getU(), bind.getV()))
				.get();
		// the equality can NEVER hold: unification failure is monotone under
		// binding growth (a structural clash stays a clash in every extension
		// of these substitutions), so the forbidden conjunction is refuted
		// FOREVER, not just for this state — the nogood discharges
		if (!minted.isDefined()) {
			return Option.of(Outcome.refuted());
		}
		Prefix residual = minted.get();
		// an EMPTY residual is not the trial passing — it means the equality
		// already holds, so the literal is entailed and crosses off. The
		// branch-failing verdict lives one level up: a nogood whose EVERY
		// literal crossed off is violated (Neq's "empty = violated"), and
		// that fold — none of the callers' survivors left — is where
		// "unchanged subs" turns into Revision.fail
		return Option.of(residual.isEmpty() ?
				Outcome.entailed(subs) :
				Outcome.owed(Propagation.resolve(residual), residual.appliedTo(subs)));
	}

	/**
	 * RE-UNIFICATION per pair, not the agenda's equality trichotomy
	 * ({@code Prefix.revalidate} reads bound-vs-open as contradiction — right
	 * for a Bind item, fatally wrong here: the trial asks whether the
	 * EQUALITY can still hold, Disequality's own reading of the same pairs).
	 */
	@Override
	public Option<Outcome> visit(Posting.Resolution resolution) {
		Substitutions current = subs;
		List<Posting> residuals = List.empty();
		for (Tuple2<LVar<?>, Term<?>> pair : resolution.getPrefix().bindings()) {
			@SuppressWarnings("unchecked")
			Option<Prefix> minted = MiniKanren.unifyPrefix(current,
					(Term<Object>) pair._1, (Term<Object>) pair._2).get();
			if (!minted.isDefined()) {
				return Option.of(Outcome.refuted());
			}
			Prefix residual = minted.get();
			if (!residual.isEmpty()) {
				residuals = residuals.append(Propagation.resolve(residual));
				current = residual.appliedTo(current);
			}
		}
		if (residuals.isEmpty()) {
			return Option.of(Outcome.entailed(current));
		}
		return Option.of(Outcome.owed(
				residuals.size() == 1 ?
						residuals.head() :
						Posting.all(residuals.toJavaArray(Posting[]::new)),
				current));
	}

	@Override
	public Option<Outcome> visit(Posting.Activation activation) {
		return Option.none();
	}

	@Override
	public Option<Outcome> visit(Posting.Absorption absorption) {
		return Option.none();
	}

	/** The conjunction steps jointly: parts thread; one refusal refutes the whole. */
	@Override
	public Option<Outcome> visit(Posting.AllOf all) {
		Substitutions current = subs;
		List<Posting> remainders = List.empty();
		for (Posting part : all.getParts()) {
			// a part with no substitution-level reading (a stated item, a
			// factor) makes the WHOLE composite answer none: the package
			// trial takes the entire literal, conservatively — mixed
			// composites forgo the fast steps of their binding parts
			Option<Outcome> stepped = step(part, current);
			if (!stepped.isDefined()) {
				return Option.none();
			}
			Outcome outcome = stepped.get();
			if (outcome.isRefuted()) {
				return Option.of(Outcome.refuted());
			}
			current = outcome.getGrown();
			if (!outcome.isEntailed()) {
				remainders = remainders.append(outcome.getRemainder());
			}
		}
		if (remainders.isEmpty()) {
			return Option.of(Outcome.entailed(current));
		}
		return Option.of(Outcome.owed(
				remainders.size() == 1 ?
						remainders.head() :
						Posting.all(remainders.toJavaArray(Posting[]::new)),
				current));
	}
}
