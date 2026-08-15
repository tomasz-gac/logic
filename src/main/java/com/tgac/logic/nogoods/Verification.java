package com.tgac.logic.nogoods;

// ABOUTME: The verification step: impose a nogood's literals on a scratch package,
// ABOUTME: read each imposition three ways, route the four moves. Neq generalized.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.goals.Package;
import io.vavr.Tuple2;
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
	 * the branch fails; otherwise the kept list — survivors simplified to their
	 * remainders, satisfied nogoods absent.
	 *
	 * <p>Dispatch is PER NOGOOD, and so is the settle: binding-shaped nogoods
	 * verify synchronously against the raw base (both hard verdicts are
	 * monotone under binding growth, so pending agenda items can only convert
	 * owed into decided later — the delay-safe direction, and a violation
	 * here fails the branch before any settle is paid). Only the
	 * package-shaped residue settles the base — evaluation and comparison
	 * need quiescence there, so the pending ITEMS complete on the copy (runs
	 * are search and stay with the real drain); a settle failure means the
	 * branch is doomed on the same items, deterministically.
	 */
	public static Fiber<Option<List<Nogood>>> verify(List<Nogood> nogoods, Package state) {
		Tuple2<List<Nogood>, List<Nogood>> byShape =
				nogoods.partition(n -> Trial.bindingShaped(n.getForbidden()));
		// the binding subset answers through the synchronous face — the sync
		// gate is a typed code path, not an eagerness property
		Option<List<Nogood>> bindingKept = foldNow(byShape._1, state);
		if (!bindingKept.isDefined()) {
			return Fiber.done(Option.none());
		}
		Option<List<Nogood>> minimal = bindingKept.map(kept -> pruneSubsumed(kept, state));
		if (byShape._2.isEmpty()) {
			return Fiber.done(minimal);
		}
		return Propagation.settled(state).flatMap(settled -> !settled.isDefined() ?
				Fiber.done(Option.none()) :
				fold(byShape._2, settled.get())
						.map(packagedKept -> packagedKept.map(minimal.get()::appendAll)));
	}

	/**
	 * Cross-nogood subsumption, Neq's removeSubsumed under the trial: ¬F_B
	 * subsumes ¬F_A whenever F_A implies F_B, witnessed by ASSUMING A's
	 * forbidden (the trial's own grown package — every survivor here is owed,
	 * so the assumption is exactly its residual bindings applied) and reading
	 * B's forbidden as entailed against it. Binding-shaped survivors only, so
	 * every trial is Done and the assumption is exact; a mutual pair keeps its
	 * later copy — head checks against kept AND pending, Neq's own tie-break.
	 *
	 * <p>A store-shaped literal claims nothing: the synchronous face answers
	 * None, so a broken shape assumption turns into a kept nogood — wider,
	 * never wrong.
	 */
	static List<Nogood> pruneSubsumed(List<Nogood> nogoods, Package base) {
		List<Nogood> kept = List.empty();
		List<Nogood> pending = nogoods;
		while (!pending.isEmpty()) {
			Nogood head = pending.head();
			pending = pending.tail();
			if (!subsumed(head, kept.appendAll(pending), base)) {
				kept = kept.append(head);
			}
		}
		return kept;
	}

	private static boolean subsumed(Nogood nogood, List<Nogood> others, Package base) {
		Option<Package> assumed = Trial.now(nogood.getForbidden(), base)
				.map(Trial.Outcome::getGrown)
				.filter(grown -> grown != null);
		return assumed.isDefined() && others.exists(other ->
				Trial.now(other.getForbidden(), assumed.get())
						.map(Trial.Outcome::isEntailed)
						.getOrElse(false));
	}

	/** The binding pass: every trial answers now, the fold is a plain loop. */
	private static Option<List<Nogood>> foldNow(List<Nogood> nogoods, Package base) {
		List<Nogood> kept = List.empty();
		for (Nogood nogood : nogoods) {
			Trial.Outcome outcome = Trial.now(nogood.getForbidden(), base)
					.getOrElseThrow(() -> new IllegalStateException(
							"the binding pass met a store-shaped nogood"));
			if (outcome.isEntailed()) {
				return Option.none();
			}
			if (!outcome.isRefuted()) {
				kept = kept.append(Nogood.of(outcome.getRemainder()));
			}
		}
		return Option.of(kept);
	}

	private static Fiber<Option<List<Nogood>>> fold(List<Nogood> nogoods, Package base) {
		return nogoods.foldLeft(
				Fiber.done(Option.of(List.empty())),
				(acc, nogood) -> acc.flatMap(kept -> kept.isDefined() ?
						Trial.trial(nogood.getForbidden(), base).map(outcome ->
								outcome.isRefuted() ?
										Option.of(kept.get()) :
										outcome.isEntailed() ?
												Option.<List<Nogood>> none() :
												Option.of(kept.get().append(Nogood.of(outcome.getRemainder())))) :
						Fiber.done(kept)));
	}

}
