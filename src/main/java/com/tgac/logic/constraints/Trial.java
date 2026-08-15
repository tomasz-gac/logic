package com.tgac.logic.constraints;

// ABOUTME: The shared trial, one core with two faces: binding rows answer through
// ABOUTME: the synchronous now(), store rows impose on the scratch, conjuncts thread.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import io.vavr.collection.LinkedHashMap;
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
 * store trials. These rows ARE the synchronous face ({@link #now}); the
 * fiber lane wraps them in {@code Fiber.done}, so no consumer's
 * synchronicity depends on how flatMap composes.
 *
 * <p>The STORE rows impose on the scratch and classify by the change
 * reading, keeping their ORIGINAL selves when owed — nothing is read back
 * out of a factor. A CONJUNCT threads its parts through the growing scratch,
 * so a mixed nogood crosses off its decided parts at part granularity.
 */
public final class Trial implements Posting.Visitor<Fiber<Trial.Outcome>> {

	private final Package scratch;

	Trial(Package scratch) {
		this.scratch = scratch;
	}

	public static Fiber<Outcome> trial(Posting literal, Package scratch) {
		return literal.accept(new Trial(scratch));
	}

	/** Steps at the substitution level — no package trial will be needed. */
	private static final Posting.Visitor<Boolean> BINDING_SHAPED = new Posting.Visitor<Boolean>() {
		@Override
		public Boolean visit(UnifyGoal<?> unification) {
			return true;
		}

		@Override
		public Boolean visit(Posting.Resolution resolution) {
			return true;
		}

		@Override
		public Boolean visit(Posting.Activation activation) {
			return false;
		}

		@Override
		public Boolean visit(Posting.Absorption absorption) {
			return false;
		}

		@Override
		public Boolean visit(Posting.AllOf all) {
			return all.getParts().forAll(part -> part.accept(this));
		}
	};

	public static boolean bindingShaped(Posting literal) {
		return literal.accept(BINDING_SHAPED);
	}

	/**
	 * The trial as doom oracle: a posting whose trial answers refuted can
	 * never hold — refutation is monotone under binding growth, so failure
	 * found at pricing is failure forever. Only the synchronous face may
	 * claim doom; a store-shaped literal claims nothing, the delay-safe
	 * direction. The dual reading — entailed through the same face — is the
	 * exclusion door's born-violated check, the same guard on the opposite
	 * verdict.
	 */
	public static boolean doomed(Posting literal, Package p) {
		return now(literal, p)
				.map(Outcome::isRefuted)
				.getOrElse(false);
	}

	/**
	 * The binding-shaped partition's synchronous face: a binding-shaped
	 * literal ANSWERS NOW — the mintings ground walks, the conjunct fold is
	 * a loop — and a store-shaped literal claims nothing. ONE implementation
	 * of the binding rows lives here; the fiber lane wraps these outcomes in
	 * {@code Fiber.done}, never recomputes them.
	 */
	public static Option<Outcome> now(Posting literal, Package scratch) {
		return bindingShaped(literal) ?
				Option.of(literal.accept(new Now(scratch))) :
				Option.none();
	}

	/** Refuted: {@code remainder == null} and not entailed. */
	@Value
	public static class Outcome {
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

		public boolean isRefuted() {
			return remainder == null && !entailed;
		}
	}

	@Override
	public Fiber<Outcome> visit(UnifyGoal<?> unification) {
		return Fiber.done(new Now(scratch).visit(unification));
	}

	@Override
	public Fiber<Outcome> visit(Posting.Resolution resolution) {
		return Fiber.done(new Now(scratch).visit(resolution));
	}

	/**
	 * The binding rows' one implementation. UNIFICATION rows answer at the
	 * substitution level; a RESOLUTION re-unifies per pair, not the agenda's
	 * equality trichotomy ({@code Prefix.revalidate} reads bound-vs-open as
	 * contradiction — right for a Bind item, fatally wrong here: the trial
	 * asks whether the EQUALITY can still hold, disequality's own reading of
	 * the same pairs); a binding-shaped CONJUNCT threads its parts through
	 * the growing scratch in a plain loop. Store rows are unreachable behind
	 * the {@link #bindingShaped} gate.
	 */
	private static final class Now implements Posting.Visitor<Outcome> {

		private final Package scratch;

		Now(Package scratch) {
			this.scratch = scratch;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Outcome visit(UnifyGoal<?> unification) {
			UnifyGoal<Object> bind = (UnifyGoal<Object>) unification;
			Option<Prefix> minted = (bind.isNoCheck() ?
					MiniKanren.unifyPrefixUnsafe(scratch.substitution(), bind.getU(), bind.getV()) :
					MiniKanren.unifyPrefix(scratch.substitution(), bind.getU(), bind.getV()))
					.ground();
			// the equality can NEVER hold: unification failure is monotone under
			// binding growth (a structural clash stays a clash in every extension
			// of these substitutions), so the forbidden conjunction is refuted
			// FOREVER, not just for this state — the nogood discharges
			if (!minted.isDefined()) {
				return Outcome.refuted();
			}
			// an EMPTY residual is not the trial passing — the literal is
			// entailed and crosses off; the branch-failing verdict (no survivors
			// left = violated) is the caller's fold
			Prefix residual = minted.get();
			return residual.isEmpty() ?
					Outcome.entailed(scratch) :
					Outcome.owed(Propagation.resolve(residual),
							withSubstitutions(scratch, residual.appliedTo(scratch.substitution())));
		}

		@Override
		public Outcome visit(Posting.Resolution resolution) {
			Substitutions current = scratch.substitution();
			List<Posting> residuals = List.empty();
			for (Tuple2<com.tgac.logic.unification.LVar<?>, Term<?>> pair : resolution.getPrefix().bindings()) {
				@SuppressWarnings("unchecked")
				Option<Prefix> minted = MiniKanren.unifyPrefix(current,
						(Term<Object>) pair._1, (Term<Object>) pair._2).ground();
				if (!minted.isDefined()) {
					return Outcome.refuted();
				}
				Prefix residual = minted.get();
				if (!residual.isEmpty()) {
					residuals = residuals.append(Propagation.resolve(residual));
					current = residual.appliedTo(current);
				}
			}
			if (residuals.isEmpty()) {
				return Outcome.entailed(withSubstitutions(scratch, current));
			}
			return Outcome.owed(
					residuals.size() == 1 ?
							residuals.head() :
							Posting.all(residuals.toJavaArray(Posting[]::new)),
					withSubstitutions(scratch, current));
		}

		@Override
		public Outcome visit(Posting.AllOf all) {
			List<Posting> remainders = List.empty();
			Package current = scratch;
			for (Posting part : all.getParts()) {
				Outcome outcome = part.accept(new Now(current));
				if (outcome.isRefuted()) {
					return Outcome.refuted();
				}
				if (!outcome.isEntailed()) {
					remainders = remainders.append(outcome.getRemainder());
				}
				current = outcome.getGrown();
			}
			return assembled(remainders, current);
		}

		@Override
		public Outcome visit(Posting.Activation activation) {
			throw notBindingShaped(activation);
		}

		@Override
		public Outcome visit(Posting.Absorption absorption) {
			throw notBindingShaped(absorption);
		}

		private static IllegalStateException notBindingShaped(Posting literal) {
			return new IllegalStateException("now over a store-shaped literal: " + literal);
		}
	}

	/**
	 * Store factors deliberately do NOT hear these bindings: staleness only
	 * shifts verdicts toward "owed", the delay-safe direction.
	 */
	private static Package withSubstitutions(Package scratch, Substitutions grown) {
		return Package.of(grown, scratch.getStores());
	}

	/** The conjunct folds' shared terminal: survivors re-conjoined or entailed. */
	private static Outcome assembled(List<Posting> remainders, Package current) {
		return remainders.isEmpty() ?
				Outcome.entailed(current) :
				Outcome.owed(remainders.size() == 1 ?
						remainders.head() :
						Posting.all(remainders.toJavaArray(Posting[]::new)),
						current);
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
		return imposed(literal, scratch).map(worlds -> {
			if (worlds.isEmpty()) {
				return Outcome.refuted();
			}
			if (worlds.size() > 1) {
				return Outcome.owed(literal, scratch);
			}
			Package grown = worlds.head();
			return unchanged(scratch, grown) ?
					Outcome.entailed(grown) :
					Outcome.owed(literal, grown);
		});
	}

	/** Parts thread through the growing scratch; one refusal refutes the whole. */
	@Override
	public Fiber<Outcome> visit(Posting.AllOf all) {
		return bindingShaped(all) ?
				Fiber.done(new Now(scratch).visit(all)) :
				parts(all.getParts(), List.empty(), scratch);
	}

	private static Fiber<Outcome> parts(List<Posting> pending, List<Posting> remainders, Package current) {
		if (pending.isEmpty()) {
			return Fiber.done(assembled(remainders, current));
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

	/**
	 * The worlds the imposition delivered, grounded through the protocol
	 * home ({@link Exhaustion#collected}): a fresh workforce claim, so
	 * completion is honest even when the imposition wakes suspension bodies
	 * (arbitrary goals, may spawn). Empty = the run stayed silent: the
	 * imposition failed.
	 */
	public static Fiber<List<Package>> imposed(Posting literal, Package scratch) {
		return Exhaustion.collected(literal.apply(scratch))
				.map(List::ofAll);
	}

	/**
	 * Sound because every piece of solver knowledge lives IN the package —
	 * substitutions, factors, parked suspensions, tables — so an imposition
	 * that added knowledge necessarily perturbs the structure, and equality
	 * witnesses "nothing new". Errs only toward "changed" (bookkeeping
	 * growth, representation drift), the conservative direction — a missed
	 * entailment only delays: stores re-verify on every revise and the
	 * ground floor decides by answer time. If solver knowledge ever lives
	 * outside the Package, this classifier is where that breaks silently.
	 *
	 * <p>Exactness at the points this runs rests on the imposition law
	 * (idempotent normalization, the ground floor, no silent swallowing —
	 * the logic laws kit's claims): verification runs on post-drain
	 * packages, quiescent hence normalized, so an entailed imposition
	 * cannot drift. Per-factor mutual leq (each store's own Absorbable
	 * order) remains available as a drift-immune refinement — pure
	 * optimization, buying earliness on the delay side.
	 */
	static boolean unchanged(Package before, Package after) {
		return before == after
				|| before.equals(after)
				|| before.substitution().equals(after.substitution())
						&& knowledge(before).equals(knowledge(after));
	}

	/**
	 * An empty store is not knowledge: an imposition whose only trace is the
	 * REGISTRATION of a store it then left empty (the inner exclusion of a
	 * double negation discarding its nogood against a NogoodConstraints-stripped
	 * scratch) has proven its content already holds — reading the empty
	 * container as change would keep the literal owed forever and let ground
	 * violations render silently.
	 */
	// TODO(the human, August 2026): further investigation owed — whether other
	//   bookkeeping shapes should be invisible to this comparison, and whether
	//   knowledge comparison belongs on Package once more clients appear.
	private static LinkedHashMap<Class<? extends Packaged>, Packaged> knowledge(Package p) {
		return p.getStores().filter(entry -> !(entry._2 instanceof ConstraintStore
				&& ((ConstraintStore) entry._2).isEmpty()));
	}
}
