package com.tgac.logic.nogoods;

// ABOUTME: The verification step: impose a nogood's literals on a scratch package,
// ABOUTME: read each imposition three ways, route the four moves. Neq generalized.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.goals.Packaged;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Neq's verification with literals for pairs and the scratch for the trial —
 * the same signatures, contract for contract: {@link #verify} is
 * verifyAndSimplify (none = a nogood is violated, the branch fails; the kept
 * list holds the survivors, discarded nogoods simply absent), {@link #trial} is
 * unifyConstraints (none = the nogood is subsumed fully, discard; empty = every
 * literal already holds, violated; survivors = the simplified nogood's
 * literals). The store slice maps verify onto {@link
 * com.tgac.logic.constraints.store.Revision} in one line: none → fail,
 * kept → updated.
 *
 * <p>A literal is imposed on the scratch and read three ways: the imposition
 * FAILS → the forbidden conjunction is refuted; it changes NOTHING → that
 * literal already holds, crossed off (lawful by monotonicity: knowledge only
 * grows, so an entailed literal stays entailed); it brings NEW knowledge →
 * still owed, the literal survives as its ORIGINAL self — nothing is ever
 * read back out of the scratch. Sequential imposition threads bindings across
 * literals sharing variables — the jointness of Neq's whole-record trial.
 *
 * <p>The change detection is exact for bindings (an entailed unification
 * resolves an empty prefix and returns the package untouched). A stated item
 * always changes the store it enters, so statement literals classify
 * conservatively toward "still owed" — a missed cross-off keeps the nogood
 * wider, never wrong.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Verification {

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

	/**
	 * Every nogood re-verified against the state: none = some nogood is violated,
	 * the branch fails; otherwise the kept list — survivors simplified to their
	 * remainders, satisfied nogoods absent.
	 *
	 * <p>A store whose every literal is binding-shaped verifies at the
	 * SUBSTITUTION level — synchronous, no scratch package, no settle: both
	 * hard verdicts are monotone under binding growth, so pending agenda items
	 * can only convert owed into decided later, the delay-safe direction.
	 */
	public static Fiber<Option<List<Nogood>>> verify(List<Nogood> nogoods, Package state) {
		if (nogoods.forAll(n -> n.getLiterals().forAll(l -> l.accept(BINDING_SHAPED)))) {
			return Fiber.done(bindingVerify(nogoods, state.substitution()));
		}
		// evaluation and comparison both need quiescence: the caller may sit
		// mid-drain, so the base COMPLETES the pending items first (runs are
		// search and stay with the real drain). A settle failure means the
		// branch is doomed on the same items, deterministically — report the
		// veto now and spare the real drain the recomputation
		return Propagation.settled(state).flatMap(settled -> !settled.isDefined() ?
				Fiber.done(Option.none()) :
				nogoods.foldLeft(
						Fiber.done(Option.of(List.empty())),
						(acc, nogood) -> acc.flatMap(kept -> kept.isDefined() ?
								verificationStep(settled.get(), kept.get(), nogood) :
								Fiber.done(kept))));
	}

	/** The whole store at the substitution level: Neq's verifyAndSimplify re-homed. */
	private static Option<List<Nogood>> bindingVerify(List<Nogood> nogoods, Substitutions subs) {
		List<Nogood> kept = List.empty();
		for (Nogood nogood : nogoods) {
			Substitutions scratch = subs;
			List<Posting> survivors = List.empty();
			boolean refuted = false;
			for (Posting literal : nogood.getLiterals()) {
				SubstitutionTrial.Outcome outcome = SubstitutionTrial.step(literal, scratch).get();
				if (outcome.isRefuted()) {
					refuted = true;
					break;
				}
				scratch = outcome.getGrown();
				if (!outcome.isEntailed()) {
					survivors = survivors.append(outcome.getRemainder());
				}
			}
			if (refuted) {
				continue;
			}
			if (survivors.isEmpty()) {
				return Option.none();
			}
			kept = kept.append(Nogood.of(survivors));
		}
		return Option.of(kept);
	}

	/** One nogood against the state: the kept list grown by the nogood's verdict. */
	private static Fiber<Option<List<Nogood>>> verificationStep(
			Package state, List<Nogood> kept, Nogood nogood) {
		return trial(nogood.getLiterals(), List.empty(), state)
				.map(delta -> !delta.isDefined() ?
						Option.of(kept) :
						delta.get().isEmpty() ?
								Option.none() :
								Option.of(kept.append(Nogood.of(delta.get()))));
	}

	/**
	 * The nogood's literals imposed sequentially on the scratch: none = an
	 * imposition failed, the forbidden conjunction is refuted — the nogood is
	 * subsumed fully by the state; empty = every literal already holds — the
	 * nogood is violated; otherwise the surviving literals, entailed ones
	 * crossed off.
	 */
	static Fiber<Option<List<Posting>>> trial(
			List<Posting> pending,
			List<Posting> survivors,
			Package scratch) {
		if (pending.isEmpty()) {
			return Fiber.done(Option.of(survivors));
		}
		Posting literal = pending.head();
		Option<SubstitutionTrial.Outcome> fast = SubstitutionTrial.step(literal, scratch.substitution());
		if (fast.isDefined()) {
			SubstitutionTrial.Outcome outcome = fast.get();
			if (outcome.isRefuted()) {
				return Fiber.done(Option.none());
			}
			// store factors deliberately do NOT hear these bindings: staleness
			// only shifts verdicts toward "owed", the delay-safe direction
			return outcome.isEntailed() ?
					trial(pending.tail(), survivors,
							Package.of(outcome.getGrown(), scratch.getStores())) :
					trial(pending.tail(), survivors.append(outcome.getRemainder()),
							Package.of(outcome.getGrown(), scratch.getStores()));
		}
		return imposed(literal, scratch).flatMap(worlds -> {
			if (worlds.isEmpty()) {
				return Fiber.done(Option.none());
			}
			if (worlds.size() > 1) {
				// the imposition woke something that forked: no single world
				// to thread. Conservative on both counts — the literal stays
				// owed (never a false cross-off) and later literals verify
				// against the unthreaded scratch (missed jointness only ever
				// keeps more)
				return trial(pending.tail(), survivors.append(literal), scratch);
			}
			Package grown = worlds.head();
			return unchanged(scratch, grown) ?
					trial(pending.tail(), survivors, scratch) :
					trial(pending.tail(), survivors.append(literal), grown);
		});
	}

	/**
	 * The worlds the imposition delivered, grounded through the protocol
	 * home ({@link Exhaustion#collected}): a fresh workforce claim, so
	 * completion is honest even when the imposition wakes suspension bodies
	 * (arbitrary goals, may spawn). Empty = the run stayed silent: the
	 * imposition failed.
	 */
	static Fiber<List<Package>> imposed(Posting literal, Package scratch) {
		return Exhaustion.collected(literal.apply(scratch))
				.map(List::ofAll);
	}

	/**
	 * Sound because every piece of solver knowledge lives IN the package —
	 * substitutions, factors, parked suspensions, tables — so an imposition
	 * that added knowledge necessarily perturbs the structure, and equality
	 * witnesses "nothing new". Errs only toward "changed" (bookkeeping
	 * growth, representation drift), the conservative direction — a missed
	 * entailment only delays: nogoods re-verify on every revise and the
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
	private static boolean unchanged(Package before, Package after) {
		return before == after
				|| before.equals(after)
				|| before.substitution().equals(after.substitution())
						&& knowledge(before).equals(knowledge(after));
	}

	/**
	 * An empty store is not knowledge: an imposition whose only trace is the
	 * REGISTRATION of a store it then left empty (the inner exclusion of a
	 * double negation discarding its nogood against a Nogoods-stripped
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
