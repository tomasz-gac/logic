package com.tgac.logic.nogoods;

// ABOUTME: The nogood store's faces over the verification core: normalize is verify
// ABOUTME: wrapped into Revision, revise is normalize by another trigger.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Constrained;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Name;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * A theory of nogoods, held conjunctively — the package's own ∧. The value
 * plane is the resident {@link Theory}: meet is union with same-surface
 * fusion and subsumption deletion (a dominated nogood drops — fewer trials,
 * same knowledge), leq is the covering order. The execution-plane normal
 * form is wholesale re-verification: {@link Verification#verify} wrapped
 * into {@link Revision} — none → fail, the kept set unchanged → unchanged,
 * otherwise the kept set replaces the factor.
 *
 * <p>Verification imposes on a scratch that NEVER carries this store: nogoods
 * examined inside a scratch answer conservatively by not being there — the
 * depth-one ruling (nogood-store.md §5) by construction, and the recursion a
 * resident nogood store would cause (its own revise re-verifying inside every
 * trial) is unrepresentable.
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class NogoodConstraints implements Factor<NogoodConstraints> {
	public static final NogoodConstraints EMPTY = NogoodConstraints.of(LinkedHashSet.empty());
	private final Theory<NogoodConstraints> theory;

	public static NogoodConstraints of(LinkedHashSet<Nogood> nogoods) {
		return new NogoodConstraints(Theory.of(nogoods));
	}

	public static Package register(Package a) {
		return Constraint.register(a, EMPTY);
	}

	/**
	 * The resident view: every atom flattened to single-conjunct nogoods —
	 * the digested form verification and reify read. Same-surface atoms may
	 * have fused in the theory; execution sees their conjuncts one by one.
	 */
	public LinkedHashSet<Nogood> getNogoods() {
		return residents().collect(LinkedHashSet.collector());
	}

	private Stream<Nogood> residents() {
		return theory.kind(Nogood.class)
				.flatMap(nogood -> nogood.getForbidden().size() == 1 ?
						Stream.of(nogood) :
						nogood.getForbidden().toJavaStream().map(Nogood::of));
	}

	@Override
	public NogoodConstraints absorb(Theory<NogoodConstraints> incoming) {
		return new NogoodConstraints(theory.meet(incoming));
	}

	@Override
	public Fiber<NogoodConstraints> rename(Renaming renaming) {
		return theory.rename(renaming).map(NogoodConstraints::new);
	}

	@Override
	public boolean isEmpty() {
		return theory.isEmpty();
	}

	@Override
	public Theory<NogoodConstraints> theory() {
		return theory;
	}

	@Override
	public NogoodConstraints meet(Atom<NogoodConstraints> c) {
		return new NogoodConstraints(theory.meet(Theory.of(List.of((Nogood) c))));
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	@Override
	public Fiber<Revision> normalize(Package state) {
		return Verification.verify(residents(), state.withoutStore(NogoodConstraints.class))
				.map(kept -> kept.isDefined() ?
						revisedTo(LinkedHashSet.ofAll(kept.get())) :
						Revision.fail());
	}

	private Revision revisedTo(LinkedHashSet<Nogood> kept) {
		NogoodConstraints revised = NogoodConstraints.of(kept);
		return revised.equals(this) ?
				Revision.unchanged() :
				Revision.updated(revised);
	}

	@Override
	public Fiber<Revision> normalize(Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
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
	public <A> Term<A> reify(Term<A> unifiable, Renaming renaming, Package s) {
		// renameSubstitutions is the answer's canonical seed: a live name it
		// binds is part of the rendered answer
		List<Atom<?>> residuals = List.empty();
		for (Nogood nogood : residents().collect(Collectors.toList())) {
			List<Posting> kept = getUnboundNames(renaming, s, nogood);
			if (kept.isEmpty()) {
				continue;
			}
			Map<Name<?>, Term<?>> display = renameKept(renaming, s, kept);
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

	private static List<Posting> getUnboundNames(Renaming renaming, Package s, Nogood nogood) {
		return literals(nogood)
				.filter(literal -> {
					java.util.List<Term<?>> names = literal.terms()
							.flatMap(term -> s.substitution().namesIn(term))
							.map(name -> (Term<?>) name)
							.collect(Collectors.toList());
					return !names.isEmpty() && names.stream()
							.allMatch(renaming::renames);
				});
	}

	private static Map<Name<?>, Term<?>> renameKept(Renaming renaming, Package s, List<Posting> kept) {
		return kept.toJavaStream()
				.flatMap(Posting::terms)
				.flatMap(term -> s.substitution().namesIn(term))
				// one name may appear in several kept literals; walk(name) is
				// deterministic, so both occurrences agree — first wins
				.collect(Collectors.toMap(Function.identity(), renaming::target,
						(first, same) -> first,
						LinkedHashMap::new));
	}

	private static List<Posting> literals(Nogood nogood) {
		return nogood.conjunct() instanceof Posting.AllOf ?
				((Posting.AllOf) nogood.conjunct()).getParts() :
				List.of(nogood.conjunct());
	}
}
