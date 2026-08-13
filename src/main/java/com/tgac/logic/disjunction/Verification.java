package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store's fold: each disjunct's alternatives tried
// ABOUTME: independently, the trial read straight — eliminate, discharge, unit.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.goals.Package;
import io.vavr.Tuple;
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
	public static Fiber<Option<Tuple2<List<Disjunct>, List<Posting>>>> verify(
			List<Disjunct> disjuncts, Package state) {
		Tuple2<List<Disjunct>, List<Disjunct>> byShape = disjuncts.partition(
				d -> d.getAlternatives().forAll(Trial::bindingShaped));
		return fold(byShape._1, state).flatMap(binding -> {
			if (!binding.isDefined()) {
				return Fiber.done(Option.none());
			}
			if (byShape._2.isEmpty()) {
				return Fiber.done(binding);
			}
			return Propagation.settled(state).flatMap(settled -> !settled.isDefined() ?
					Fiber.done(Option.none()) :
					fold(byShape._2, settled.get()).map(packaged ->
							packaged.map(p -> Tuple.of(
									binding.get()._1.appendAll(p._1),
									binding.get()._2.appendAll(p._2)))));
		});
	}

	private static Fiber<Option<Tuple2<List<Disjunct>, List<Posting>>>> fold(
			List<Disjunct> pending, Package base) {
		return pending.foldLeft(
				Fiber.done(Option.of(Tuple.of(List.<Disjunct> empty(), List.<Posting> empty()))),
				(acc, disjunct) -> acc.flatMap(state -> !state.isDefined() ?
						Fiber.done(state) :
						foldOne(disjunct, base).map(fold -> {
							if (fold.isFailed()) {
								return Option.none();
							}
							if (fold.getUnit() != null) {
								return Option.of(Tuple.of(state.get()._1,
										state.get()._2.append(fold.getUnit())));
							}
							if (fold.getKept() != null) {
								return Option.of(Tuple.of(state.get()._1.append(fold.getKept()),
										state.get()._2));
							}
							return state;
						})));
	}

	/** One disjunct's fold: refuted eliminated, entailed discharges, owed shrinks. */
	private static Fiber<Fold> foldOne(Disjunct disjunct, Package base) {
		return disjunct.getAlternatives().foldLeft(
						Fiber.done(Option.of(List.<Posting> empty())),
						(acc, alternative) -> acc.flatMap(survivors -> !survivors.isDefined() ?
								Fiber.done(survivors) :
								Trial.trial(alternative, base).map(outcome ->
										outcome.isEntailed() ?
												Option.<List<Posting>> none() :
												outcome.isRefuted() ?
														survivors :
														Option.of(survivors.get()
																.append(outcome.getRemainder())))))
				.map(survivors -> {
					if (!survivors.isDefined()) {
						return Fold.DISCHARGED;
					}
					List<Posting> left = survivors.get();
					if (left.isEmpty()) {
						return Fold.FAILED;
					}
					if (left.size() == 1) {
						return new Fold(null, left.head(), false);
					}
					return new Fold(new Disjunct(left), null, false);
				});
	}

	@Value
	private static class Fold {
		static final Fold DISCHARGED = new Fold(null, null, false);
		static final Fold FAILED = new Fold(null, null, true);
		Disjunct kept;
		Posting unit;
		boolean failed;
	}
}
