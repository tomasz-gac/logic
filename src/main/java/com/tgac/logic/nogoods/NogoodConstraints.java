package com.tgac.logic.nogoods;

// ABOUTME: The nogood store's faces over the verification core: normalize is verify
// ABOUTME: wrapped into Revision, revise is normalize by another trigger.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Constrained;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.constraints.store.Verifier;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Name;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A theory of nogoods, held conjunctively — the package's own ∧. The value
 * plane is the pair's resident {@link Theory}: the statement door's meet is
 * union with same-surface fusion and subsumption deletion (a dominated nogood
 * drops — fewer trials, same knowledge), leq is the covering order. The
 * execution-plane normal form is wholesale re-verification:
 * {@link Verification#verify} wrapped into {@link Revision} — none → fail,
 * the kept set unchanged → unchanged, otherwise the kept set replaces the
 * theory.
 *
 * <p>Verification imposes on a scratch that NEVER carries this store: nogoods
 * examined inside a scratch answer conservatively by not being there — the
 * depth-one ruling (nogood-store.md §5) by construction, and the recursion a
 * resident nogood store would cause (its own revise re-verifying inside every
 * trial) is unrepresentable.
 */
public final class NogoodConstraints implements Factor<NogoodConstraints>, Verifier {
	public static final NogoodConstraints EMPTY = new NogoodConstraints();

	private NogoodConstraints() {
	}

	public static Package register(Package a) {
		return Constraint.register(a, EMPTY);
	}

	/**
	 * The resident view: every atom flattened to single-conjunct nogoods —
	 * the digested form verification and reify read. Same-surface atoms may
	 * have fused in the theory; execution sees their conjuncts one by one.
	 */
	public static LinkedHashSet<Nogood> getNogoods(Theory<NogoodConstraints> theory) {
		return residents(theory).collect(LinkedHashSet.collector());
	}

	private static Stream<Nogood> residents(Theory<NogoodConstraints> theory) {
		return theory.kind(Nogood.class)
				.flatMap(nogood -> nogood.getForbidden().size() == 1 ?
						Stream.of(nogood) :
						nogood.getForbidden().toJavaStream().map(Nogood::of));
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	@Override
	public Fiber<Revision> normalize(Theory<NogoodConstraints> incoming, Package state) {
		return Verification.verify(residents(incoming), state.withoutStore(NogoodConstraints.class))
				.map(kept -> kept.isDefined() ?
						revisedTo(incoming, LinkedHashSet.ofAll(kept.get())) :
						Revision.fail());
	}

	private Revision revisedTo(Theory<NogoodConstraints> resident, LinkedHashSet<Nogood> kept) {
		Theory<NogoodConstraints> revised = Theory.of(kept);
		return revised.equals(resident) ?
				Revision.unchanged() :
				Revision.updated(Constraint.of(revised, this));
	}

	@Override
	public Fiber<Revision> normalize(Theory<NogoodConstraints> incoming, Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
		return normalize(incoming, state);
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
	public <A> Term<A> reify(Theory<NogoodConstraints> incoming, Term<A> unifiable, Renaming renaming, Package s) {
		// renameSubstitutions is the answer's canonical seed: a live name it
		// binds is part of the rendered answer
		List<Atom<?>> residuals = List.empty();
		for (Nogood nogood : residents(incoming).collect(Collectors.toList())) {
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
