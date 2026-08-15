package com.tgac.logic.nogoods;

// ABOUTME: The nogood store's faces over the verification core: normalize is verify
// ABOUTME: wrapped into Revision, revise is normalize by another trigger.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Constrained;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unknown;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A bag of nogoods, held conjunctively — the package's own ∧. The lattice is
 * nogood union (more nogoods = more known; the derived leq is containment), and
 * the normal form is wholesale re-verification: {@link Verification#verify}
 * wrapped into {@link Revision} — none → fail, the kept set unchanged →
 * unchanged, otherwise the kept set replaces the factor.
 *
 * <p>Verification imposes on a scratch that NEVER carries this store: nogoods
 * examined inside a scratch answer conservatively by not being there — the
 * depth-one ruling (nogood-store.md §5) by construction, and the recursion a
 * resident nogood store would cause (its own revise re-verifying inside every
 * trial) is unrepresentable.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "of")
final class NogoodConstraints implements Projectable<NogoodConstraints> {
	public static final NogoodConstraints EMPTY = NogoodConstraints.of(LinkedHashSet.empty());
	private final LinkedHashSet<Nogood> nogoods;

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	/** More nogoods = more known: meet is union; wholesale re-verification keeps it exact. */
	@Override
	public NogoodConstraints meet(NogoodConstraints other) {
		return NogoodConstraints.of(nogoods.addAll(other.nogoods));
	}

	/** Nogood containment directly — the order the union-meet derives. */
	@Override
	public boolean leq(NogoodConstraints other) {
		return nogoods.containsAll(other.nogoods);
	}

	/**
	 * Lossless factoring: a nogood goes to the covered half iff every name it
	 * touches, deeply, is supplied — compound at the crossings, never
	 * distributed (nogood-store.md §7). {@code _1 ∧ _2 = this}.
	 */
	@Override
	public Tuple2<NogoodConstraints, NogoodConstraints> split(java.util.List<LVar<?>> vars) {
		Set<Unknown<?>> covered = new HashSet<>(vars);
		LinkedHashSet<Nogood> in = LinkedHashSet.empty();
		LinkedHashSet<Nogood> out = LinkedHashSet.empty();
		for (Nogood nogood : nogoods) {
			boolean fits = nogood.terms()
					.flatMap(MiniKanren::namesIn)
					.allMatch(covered::contains);
			if (fits) {
				in = in.add(nogood);
			} else {
				out = out.add(nogood);
			}
		}
		return Tuple.of(NogoodConstraints.of(in), NogoodConstraints.of(out));
	}

	/** Every nogood transcribed wrapped — literal by literal, items re-instantiated. */
	@Override
	public Fiber<NogoodConstraints> rename(Renaming renaming) {
		return nogoods.foldLeft(
						Fiber.<LinkedHashSet<Nogood>> done(LinkedHashSet.empty()),
						(acc, nogood) -> acc.flatMap(renamed ->
								nogood.rename(renaming).map(item -> renamed.add((Nogood) item))))
				.map(NogoodConstraints::of);
	}

	@Override
	public boolean isEmpty() {
		return nogoods.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return NogoodConstraints.of(nogoods.remove((Nogood) c));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return NogoodConstraints.of(nogoods.add((Nogood) c));
	}

	@Override
	public boolean contains(Stored c) {
		return c instanceof Nogood && nogoods.contains((Nogood) c);
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	@Override
	public Fiber<Revision> normalize(Package state) {
		return Verification.verify(nogoods.toList(), state.withoutStore(NogoodConstraints.class))
				.map(kept -> kept.isDefined() ?
						revisedTo(LinkedHashSet.ofAll(kept.get())) :
						Revision.fail());
	}

	private Revision revisedTo(LinkedHashSet<Nogood> kept) {
		return kept.equals(nogoods) ?
				Revision.unchanged() :
				Revision.updated(NogoodConstraints.of(kept));
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
		return normalize(state);
	}

	/** First examination is the same wholesale re-verification: a nogood born violated fails here. */
	@Override
	public Fiber<Revision> stated(Stored item, Package state) {
		return normalize(state);
	}

	/**
	 * A nogood still live about the rendered term is an expressed infinity:
	 * it ATTACHES to the answer as a residual through {@link Constrained} —
	 * expressed, never dropped — displayed through the postings' own
	 * toString. Literals about unrendered names prune from the DISPLAY copy
	 * (Neq's purify convention: display-only, the store unaffected); a nogood
	 * whose every literal pruned stays invisible, as it always was.
	 */
	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package s) {
		// renameSubstitutions is the answer's canonical seed: a live name it
		// binds is part of the rendered answer
		List<Stored> residuals = List.empty();
		for (Nogood nogood : nogoods) {
			List<Posting> kept = getUnboundNames(renameSubstitutions, s, nogood);
			if (kept.isEmpty()) {
				continue;
			}
			Map<Unknown<?>, Term<?>> display = renameKept(renameSubstitutions, s, kept);
			Nogood pruned = Nogood.of(kept.size() == 1 ?
					kept.head() :
					Posting.all(kept.toJavaArray(Posting[]::new)));
			residuals = residuals.append(pruned.rename(Renaming.of(display))
					.ground());
		}
		return residuals.isEmpty() ?
				unifiable :
				Constrained.of(unifiable, residuals);
	}

	private static List<Posting> getUnboundNames(Substitutions renameSubstitutions, Package s, Nogood nogood) {
		return literals(nogood)
				.filter(literal -> {
					java.util.List<Term<?>> names = literal.terms()
							.flatMap(term -> s.substitution().namesIn(term))
							.map(name -> (Term<?>) name)
							.collect(Collectors.toList());
					return !names.isEmpty() && names.stream()
							.allMatch(name -> renameSubstitutions.walk(name) != name);
				});
	}

	private static Map<Unknown<?>, Term<?>> renameKept(Substitutions renameSubstitutions, Package s, List<Posting> kept) {
		return kept.toJavaStream()
				.flatMap(Posting::terms)
				.flatMap(term -> s.substitution().namesIn(term))
				// one name may appear in several kept literals; walk(name) is
				// deterministic, so both occurrences agree — first wins
				.collect(Collectors.toMap(Function.identity(), renameSubstitutions::walk,
						(first, same) -> first,
						LinkedHashMap::new));
	}

	private static List<Posting> literals(Nogood nogood) {
		return nogood.getForbidden() instanceof Posting.AllOf ?
				((Posting.AllOf) nogood.getForbidden()).getParts() :
				List.of(nogood.getForbidden());
	}
}
