package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store: disjuncts held conjunctively, the straight fold
// ABOUTME: on the shared trial — eliminate, discharge, fail empty, unit-impose.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Constrained;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.store.Absorbable;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Suspension;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unknown;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * A bag of disjuncts, held conjunctively — every disjunct must be satisfied,
 * each by at least one of its alternatives. The lattice is disjunct union
 * (more disjuncts = more known; the derived leq is containment), and the
 * normal form is the STRAIGHT fold on the shared trial — the nogood store's
 * verdicts read without negation: a refuted alternative is eliminated, an
 * owed one shrinks to its remainder, an entailed one discharges the whole
 * disjunct (satisfaction is monotone), an emptied disjunct fails the branch,
 * and a single survivor is no longer a choice but a consequence — unit
 * propagation, imposed through the chokepoint by riding the revision's
 * always-ripe suspension payload.
 *
 * <p>Alternatives are rival worlds: each trials independently against the
 * same base, never through a sibling's growth. The fold imposes on a scratch
 * that NEVER carries this store — the recursion a resident disjunction store
 * would cause is unrepresentable, the nogood store's own construction.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "of")
final class DisjunctionConstraints implements Absorbable<DisjunctionConstraints> {
	public static final DisjunctionConstraints EMPTY =
			DisjunctionConstraints.of(LinkedHashSet.empty());
	private final LinkedHashSet<Disjunct> disjuncts;

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	/** More disjuncts = more known: meet is union. */
	@Override
	public DisjunctionConstraints meet(DisjunctionConstraints other) {
		return DisjunctionConstraints.of(disjuncts.addAll(other.disjuncts));
	}

	/** Disjunct containment directly — the order the union-meet derives. */
	@Override
	public boolean leq(DisjunctionConstraints other) {
		return disjuncts.containsAll(other.disjuncts);
	}

	@Override
	public boolean isEmpty() {
		return disjuncts.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return DisjunctionConstraints.of(disjuncts.remove((Disjunct) c));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return DisjunctionConstraints.of(disjuncts.add((Disjunct) c));
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof Disjunct && disjuncts.contains((Disjunct) c);
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	@Override
	public Fiber<Revision> normalize(Package state) {
		return folded(state.withoutStore(DisjunctionConstraints.class))
				.map(result -> {
					if (!result.isDefined()) {
						return Revision.fail();
					}
					LinkedHashSet<Disjunct> kept = LinkedHashSet.ofAll(result.get()._1);
					List<Posting> units = result.get()._2;
					if (kept.equals(disjuncts) && units.isEmpty()) {
						return Revision.unchanged();
					}
					Revision.Updated updated = Revision.updated(DisjunctionConstraints.of(kept));
					for (Posting unit : units) {
						// the degenerate always-ripe suspension is a plain run:
						// the survivor imposes through the chokepoint after the
						// drain, in this branch, no fork
						updated = updated.withSuspend(Suspension.of(
								Collections.emptyList(), s -> true, unit));
					}
					return updated;
				});
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
		return normalize(state);
	}

	/** First examination is the same fold: a born-satisfied disjunct discharges here. */
	@Override
	public Fiber<Revision> stated(Stored item, Package state) {
		return normalize(state);
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

	/**
	 * Dispatch is PER DISJUNCT, the nogood store's own split: disjuncts whose
	 * every alternative is binding-shaped fold synchronously against the raw
	 * base; the packaged residue settles first — evaluation needs quiescence
	 * there, and a settle failure dooms the branch on the same items.
	 */
	private Fiber<Option<Tuple2<List<Disjunct>, List<Posting>>>> folded(Package base) {
		Tuple2<List<Disjunct>, List<Disjunct>> byShape = disjuncts.toList().partition(
				d -> d.getAlternatives().forAll(Trial::bindingShaped));
		return foldAll(byShape._1, base).flatMap(binding -> {
			if (!binding.isDefined()) {
				return Fiber.done(Option.none());
			}
			if (byShape._2.isEmpty()) {
				return Fiber.done(binding);
			}
			return Propagation.settled(base).flatMap(settled -> !settled.isDefined() ?
					Fiber.done(Option.none()) :
					foldAll(byShape._2, settled.get()).map(packaged ->
							packaged.map(p -> Tuple.of(
									binding.get()._1.appendAll(p._1),
									binding.get()._2.appendAll(p._2)))));
		});
	}

	private static Fiber<Option<Tuple2<List<Disjunct>, List<Posting>>>> foldAll(
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

	/**
	 * A disjunct still live about the rendered term is an expressed
	 * alternative set: it attaches whole — pruning an alternative from
	 * DISPLAY would show a stronger disjunction than the store holds, so a
	 * disjunct renders only when every touched name is part of the answer,
	 * and stays invisible otherwise.
	 */
	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package s) {
		List<Stored> residuals = List.empty();
		for (Disjunct disjunct : disjuncts) {
			java.util.List<Term<?>> names = disjunct.terms()
					.map(term -> (Term<?>) s.substitution().walkAll(term))
					.flatMap(MiniKanren::namesIn)
					.map(name -> (Term<?>) name)
					.collect(Collectors.toList());
			boolean rendered = !names.isEmpty() && names.stream()
					.allMatch(name -> renameSubstitutions.walk(name) != name);
			if (!rendered) {
				continue;
			}
			Map<Unknown<?>, Term<?>> display = names.stream()
					.flatMap(MiniKanren::namesIn)
					.collect(Collectors.toMap(Function.identity(), renameSubstitutions::walk,
							(first, same) -> first,
							LinkedHashMap::new));
			residuals = residuals.append((Stored) disjunct.rename(Renaming.of(display)).get());
		}
		return residuals.isEmpty() ?
				unifiable :
				Constrained.of(unifiable, residuals);
	}
}
