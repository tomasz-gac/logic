package com.tgac.logic.nogoods;

// ABOUTME: The verification step: impose a nogood's literals on a scratch package,
// ABOUTME: read each imposition three ways, route the four moves. Neq generalized.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.Statement;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Package;
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

	/**
	 * Every nogood re-verified against the state: none = some nogood is violated,
	 * the branch fails; otherwise the kept list — survivors simplified,
	 * satisfied nogoods absent.
	 */
	public static Fiber<Option<List<Nogood>>> verify(List<Nogood> nogoods, Package state) {
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
	static Fiber<Option<List<Statement>>> trial(
			List<Statement> pending,
			List<Statement> survivors,
			Package scratch) {
		if (pending.isEmpty()) {
			return Fiber.done(Option.of(survivors));
		}
		Statement literal = pending.head();
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
	static Fiber<List<Package>> imposed(Statement literal, Package scratch) {
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
	// bookkeeping shapes should be invisible to this comparison, and whether
	// knowledge comparison belongs on Package once more clients appear.
	private static LinkedHashMap<Class<? extends Packaged>, Packaged> knowledge(Package p) {
		return p.getStores().filter(entry -> !(entry._2 instanceof ConstraintStore
				&& ((ConstraintStore) entry._2).isEmpty()));
	}
}
