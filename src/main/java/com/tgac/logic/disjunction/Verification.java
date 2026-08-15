package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store's fold: each disjunct's alternatives tried
// ABOUTME: independently, the trial read straight — eliminate, discharge, unit.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.goals.Package;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * The nogood store's verification with the verdicts read STRAIGHT: a refuted
 * alternative is eliminated, an owed one shrinks to its remainder, an
 * entailed one discharges the whole disjunct (satisfaction is monotone), an
 * emptied disjunct fails the branch, and a single survivor is no longer a
 * choice but a consequence — the unit list rides back to the store, which
 * imposes each through the chokepoint.
 *
 * <p>Alternatives are rival worlds: each trials independently against the
 * same base, never through a sibling's growth — the one deliberate contrast
 * with the nogood trial's conjunct threading. A conjunctive alternative
 * ({@code Posting.all}) still threads internally, inside its own hypothesis.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Verification {

	/**
	 * Every disjunct re-folded against the state: none = some disjunct
	 * emptied, the branch fails; otherwise the kept disjuncts (shrunk to
	 * their surviving alternatives, discharged ones absent) alongside the
	 * unit consequences.
	 *
	 * <p>Dispatch is PER DISJUNCT, the nogood store's own split: disjuncts
	 * whose every alternative is binding-shaped fold synchronously against
	 * the raw base; the packaged residue settles first — evaluation needs
	 * quiescence there, and a settle failure dooms the branch on the same
	 * items.
	 */
	public static Fiber<Option<Verified>> verify(List<Disjunct> disjuncts, Package state) {
		Tuple2<List<Disjunct>, List<Disjunct>> byShape = disjuncts.partition(
				d -> d.getAlternatives().forAll(Trial::bindingShaped));
		// the binding subset answers through the synchronous face — the sync
		// gate is a typed code path, not an eagerness property
		Option<Verified> binding = foldNow(byShape._1, state);
		if (!binding.isDefined()) {
			return Fiber.done(Option.none());
		}
		if (byShape._2.isEmpty()) {
			return Fiber.done(binding);
		}
		return Propagation.settled(state).flatMap(settled -> !settled.isDefined() ?
				Fiber.done(Option.none()) :
				fold(byShape._2, settled.get()).map(packaged ->
						packaged.map(binding.get()::mergedWith)));
	}

	/** The binding pass: every alternative answers now, the folds are loops. */
	private static Option<Verified> foldNow(List<Disjunct> pending, Package base) {
		Verified acc = new Verified(List.empty(), List.empty());
		for (Disjunct disjunct : pending) {
			Option<Verified> added = foldOneNow(disjunct, base).addedTo(acc);
			if (!added.isDefined()) {
				return Option.none();
			}
			acc = added.get();
		}
		return Option.of(acc);
	}

	private static Fold foldOneNow(Disjunct disjunct, Package base) {
		List<Posting> survivors = List.empty();
		for (Posting alternative : disjunct.getAlternatives()) {
			Trial.Outcome outcome = Trial.now(alternative, base)
					.getOrElseThrow(() -> new IllegalStateException(
							"the binding pass met a store-shaped alternative"));
			if (outcome.isEntailed()) {
				return Fold.DISCHARGED;
			}
			if (!outcome.isRefuted()) {
				survivors = survivors.append(outcome.getRemainder());
			}
		}
		return folded(survivors);
	}

	/** The verify result: the disjuncts that stay resident, the survivors to impose. */
	@Value
	public static class Verified {
		List<Disjunct> kept;
		List<Posting> units;

		Verified mergedWith(Verified other) {
			return new Verified(kept.appendAll(other.kept), units.appendAll(other.units));
		}
	}

	private static Fiber<Option<Verified>> fold(List<Disjunct> pending, Package base) {
		return pending.foldLeft(
				Fiber.done(Option.of(new Verified(List.empty(), List.empty()))),
				(acc, disjunct) -> acc.flatMap(verified -> !verified.isDefined() ?
						Fiber.done(verified) :
						foldOne(disjunct, base).map(fold -> fold.addedTo(verified.get()))));
	}

	/** One disjunct's fold: refuted eliminated, entailed discharges, owed shrinks. */
	private static Fiber<Fold> foldOne(Disjunct disjunct, Package base) {
		return disjunct.getAlternatives().foldLeft(
						Fiber.done(Option.of(List.<Posting> empty())),
						(acc, alternative) -> acc.flatMap(survivors ->
								!survivors.isDefined() ?
										Fiber.done(survivors) :
										Trial.trial(alternative, base).map(outcome ->
												outcome.isEntailed() ?
														Option.none() :
														outcome.isRefuted() ?
																survivors :
																Option.of(survivors.get()
																		.append(outcome.getRemainder())))))
				.map(survivors -> survivors.isDefined() ?
						folded(survivors.get()) :
						Fold.DISCHARGED);
	}

	/** The disjunct folds' shared terminal: empty fails, a singleton is a unit. */
	private static Fold folded(List<Posting> survivors) {
		if (survivors.isEmpty()) {
			return Fold.FAILED;
		}
		if (survivors.size() == 1) {
			return new Fold(null, survivors.head(), false);
		}
		return new Fold(new Disjunct(survivors), null, false);
	}

	@Value
	private static class Fold {
		static final Fold DISCHARGED = new Fold(null, null, false);
		static final Fold FAILED = new Fold(null, null, true);
		Disjunct kept;
		Posting unit;
		boolean failed;

		/** One disjunct's verdict lands in the accumulator; failure poisons it. */
		Option<Verified> addedTo(Verified acc) {
			if (failed) {
				return Option.none();
			}
			if (unit != null) {
				return Option.of(new Verified(acc.getKept(), acc.getUnits().append(unit)));
			}
			if (kept != null) {
				return Option.of(new Verified(acc.getKept().append(kept), acc.getUnits()));
			}
			return Option.of(acc);
		}
	}
}
