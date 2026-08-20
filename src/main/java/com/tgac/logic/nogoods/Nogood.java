package com.tgac.logic.nogoods;

// ABOUTME: The nogood atom: forbidden conjuncts sharing one watched surface —
// ABOUTME: each conjunct reads ¬(l₁ ∧ … ∧ lₙ); the atom is their conjunction.

import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Doomed;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.Traversable;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * {@code ¬(c₁) ∧ ¬(c₂) ∧ …} over one watched surface: the atom HOLDS its
 * conjuncts as a collection, so a theory slot is occupied by exactly one
 * nogood atom and {@link #combine} (the declared {@link Semilattice}
 * capability) is conjunct union — same-surface knowledge accumulates in
 * place. A conjunct's jointness and literal granularity live in the posting
 * itself ({@link Posting#all} for {@code ¬(l₁ ∧ … ∧ lₙ)}). The factor holds
 * SINGLE-conjunct residents (its digested form — {@code meet(Atom)}
 * flattens); multi-conjunct atoms live in plan space.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Nogood implements Atom<NogoodConstraints>, Doomed, Semilattice<Nogood> {
	LinkedHashSet<Posting> forbidden;
	HashSet<Term<?>> surface;

	/**
	 * Identity: ∧ is commutative, so a conjunct IS its literal set and a
	 * nogood is the set of those — permuted literals are the same knowledge
	 * and must be the same nogood (dedup, cross-lineage keys, and leq
	 * antisymmetry all lean on it). The postings keep their stated order
	 * for the trial and the display; {@code leq} reads these held sets.
	 */
	@EqualsAndHashCode.Include
	Set<Set<Posting>> literals;

	/**
	 * One forbidden conjunct, held FLAT: ∧ is associative, so nested
	 * {@code all}s are one conjunction — flattening at the envelope makes
	 * structural equality match semantic equality (dedup and cross-lineage
	 * key comparison would otherwise miss same-content nogoods that differ
	 * only in nesting) and keeps the trial's fast-path partition shallow.
	 * Labels unwrap on the way (presentation, outside identity).
	 */
	public static Nogood of(Posting forbidden) {
		List<Posting> flat = forbidden.accept(FLATTEN);
		return make(LinkedHashSet.of(flat.size() == 1 ?
				flat.head() :
				Posting.all(flat.toJavaArray(Posting[]::new))));
	}

	private static Nogood make(LinkedHashSet<Posting> conjuncts) {
		return new Nogood(conjuncts, surfaceOf(conjuncts), conjuncts.toJavaStream()
				.map(Nogood::literalSet)
				.collect(Collectors.toSet()));
	}

	private static HashSet<Term<?>> surfaceOf(LinkedHashSet<Posting> conjuncts) {
		return HashSet.ofAll(conjuncts.toJavaStream()
				.flatMap(Posting::terms)
				.collect(Collectors.toList()));
	}

	private static final Posting.Visitor<List<Posting>> FLATTEN =
			new Posting.Visitor<List<Posting>>() {
				@Override
				public List<Posting> visit(UnifyGoal<?> unification) {
					return List.of(unification);
				}

				@Override
				public List<Posting> visit(Posting.Resolution resolution) {
					return List.of(resolution);
				}

				@Override
				public List<Posting> visit(Posting.Activation activation) {
					return List.of(activation);
				}

				@Override
				public List<Posting> visit(Posting.Absorption absorption) {
					return List.of(absorption);
				}

				@Override
				public List<Posting> visit(Posting.AllOf all) {
					return all.getParts().flatMap(part -> part.accept(this)).toList();
				}
			};

	/** The declared capability: same-surface conjuncts union; loud otherwise. */
	@Override
	public Nogood combine(Nogood other) {
		if (!surface.equals(other.surface)) {
			throw new IllegalArgumentException(
					"nogoods on different surfaces do not combine: " + this + " vs " + other);
		}
		return make(forbidden.addAll(other.forbidden));
	}

	/** The factor-resident face: a digested nogood holds exactly one conjunct. */
	public Posting conjunct() {
		if (forbidden.size() != 1) {
			throw new IllegalStateException(
					"a factor-resident nogood holds one conjunct: " + this);
		}
		return forbidden.head();
	}

	@Override
	public Class<? extends NogoodConstraints> getFactorClass() {
		return NogoodConstraints.class;
	}

	@Override
	public String name() {
		return "nogood";
	}

	@Override
	public Traversable<Term<?>> watched() {
		return surface;
	}

	/**
	 * Nogood subsumption, covering over conjuncts: this ⊑ other iff every
	 * conjunct of {@code other} is entailed by SOME conjunct of this —
	 * ¬(A) entails ¬(A ∧ B), literal-subset per pair (flattened; literal
	 * equality is structural, so sharp only over walked literals).
	 */
	@Override
	public boolean leq(Atom<NogoodConstraints> other) {
		if (!(other instanceof Nogood)) {
			return equals(other);
		}
		return ((Nogood) other).literals.stream().allMatch(d ->
				literals.stream().anyMatch(d::containsAll));
	}

	private static Set<Posting> literalSet(Posting conjunct) {
		return new java.util.HashSet<>(conjunct instanceof Posting.AllOf ?
				((Posting.AllOf) conjunct).getParts().toJavaList() :
				Collections.singletonList(conjunct));
	}

	@Override
	public NogoodConstraints empty() {
		return NogoodConstraints.EMPTY;
	}

	/**
	 * Born-violated: a conjunct already ENTAILED is failure forever
	 * (entailment is monotone under binding growth); binding-shaped
	 * conjuncts answer through the synchronous face, store-shaped claim
	 * nothing.
	 */
	@Override
	public boolean doomed(Package p) {
		return forbidden.exists(conjunct -> Trial.now(conjunct, p)
				.map(Trial.Outcome::isEntailed)
				.getOrElse(false));
	}

	/** Each conjunct transcribes itself wrapped; the envelope follows. */
	@Override
	public Fiber<Atom<NogoodConstraints>> rename(Renaming renaming) {
		Fiber<LinkedHashSet<Posting>> renamed = Fiber.done(LinkedHashSet.empty());
		for (Posting conjunct : forbidden) {
			renamed = renamed.flatMap(acc -> conjunct.rename(renaming).map(acc::add));
		}
		return renamed.map(Nogood::make);
	}

	@Override
	public String toString() {
		return forbidden.map(c -> "¬(" + c + ")").mkString(" ∧ ");
	}
}
