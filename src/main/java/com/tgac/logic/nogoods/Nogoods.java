package com.tgac.logic.nogoods;

// ABOUTME: The nogood store's faces over the verification core: normalize is verify
// ABOUTME: wrapped into Revision, revise is normalize by another trigger.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Unknown;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
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
final class Nogoods implements Projectable<Nogoods> {
	public static final Nogoods EMPTY = Nogoods.of(LinkedHashSet.empty());
	private final LinkedHashSet<Nogood> nogoods;

	public static Package register(Package a) {
		return a.withStore(EMPTY);
	}

	/** More nogoods = more known: meet is union; wholesale re-verification keeps it exact. */
	@Override
	public Nogoods meet(Nogoods other) {
		return Nogoods.of(nogoods.addAll(other.nogoods));
	}

	/** Nogood containment directly — the order the union-meet derives. */
	@Override
	public boolean leq(Nogoods other) {
		return nogoods.containsAll(other.nogoods);
	}

	/**
	 * Lossless factoring: a nogood goes to the covered half iff every name it
	 * touches, deeply, is supplied — compound at the crossings, never
	 * distributed (nogood-store.md §7). {@code _1 ∧ _2 = this}.
	 */
	@Override
	public Tuple2<Nogoods, Nogoods> split(java.util.List<LVar<?>> vars) {
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
		return Tuple.of(Nogoods.of(in), Nogoods.of(out));
	}

	/** Every nogood transcribed wrapped — literal by literal, items re-instantiated. */
	@Override
	public Fiber<Nogoods> rename(Renaming renaming) {
		return nogoods.foldLeft(
						Fiber.<LinkedHashSet<Nogood>> done(LinkedHashSet.empty()),
						(acc, nogood) -> acc.flatMap(renamed ->
								nogood.rename(renaming).map(item -> renamed.add((Nogood) item))))
				.map(Nogoods::of);
	}

	@Override
	public boolean isEmpty() {
		return nogoods.isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return Nogoods.of(nogoods.remove((Nogood) c));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return Nogoods.of(nogoods.add((Nogood) c));
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
		return Verification.verify(nogoods.toList(), state.withoutStore(Nogoods.class))
				.map(kept -> kept.isDefined() ?
						revisedTo(LinkedHashSet.ofAll(kept.get())) :
						Revision.fail());
	}

	private Revision revisedTo(LinkedHashSet<Nogood> kept) {
		return kept.equals(nogoods) ?
				Revision.unchanged() :
				Revision.updated(Nogoods.of(kept));
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
	 * Stage wall: a nogood still live about the rendered term is an expressed
	 * infinity this stage cannot render — refuse loudly rather than deliver
	 * an answer with its condition silently dropped.
	 */
	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package s) {
		// renameSubstitutions is the answer's canonical seed: a live name it
		// binds is part of the rendered answer
		for (Nogood nogood : nogoods) {
			nogood.terms()
					.map(term -> s.substitution().walkAll(term))
					.flatMap(MiniKanren::namesIn)
					.filter(name -> renameSubstitutions.walk((Term<?>) name) != name)
					.findFirst()
					.ifPresent(name -> {
						throw new IllegalStateException(
								"unresolved nogood " + nogood + " about rendered " + name
										+ ": rendering conditional answers is not built at this stage");
					});
		}
		return unifiable;
	}
}
