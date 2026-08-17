package com.tgac.logic.nogoods;

// ABOUTME: The nogood atom: forbidden conjuncts sharing one watched surface —
// ABOUTME: each conjunct reads ¬(l₁ ∧ … ∧ lₙ); the atom is their conjunction.

import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.Traversable;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
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
public class Nogood implements Atom<NogoodConstraints>, Semilattice<Nogood> {
	LinkedHashSet<Posting> forbidden;
	HashSet<Term<?>> surface;

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
		LinkedHashSet<Posting> conjuncts = LinkedHashSet.of(flat.size() == 1 ?
				flat.head() :
				Posting.all(flat.toJavaArray(Posting[]::new)));
		return new Nogood(conjuncts, surfaceOf(conjuncts));
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
		return new Nogood(forbidden.addAll(other.forbidden), surface);
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

	@Override
	public Object payload() {
		return forbidden;
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
		LinkedHashSet<Posting> theirs = ((Nogood) other).forbidden;
		return theirs.forAll(d -> forbidden.exists(c ->
				literalSet(d).containsAll(literalSet(c))));
	}

	private static Set<Posting> literalSet(Posting conjunct) {
		return new java.util.HashSet<>(conjunct instanceof Posting.AllOf ?
				((Posting.AllOf) conjunct).getParts().toJavaList() :
				Collections.singletonList(conjunct));
	}

	/** Each conjunct transcribes itself wrapped; the envelope follows. */
	@Override
	public Fiber<Atom<NogoodConstraints>> rename(Renaming renaming) {
		Fiber<LinkedHashSet<Posting>> renamed = Fiber.done(LinkedHashSet.empty());
		for (Posting conjunct : forbidden) {
			renamed = renamed.flatMap(acc -> conjunct.rename(renaming).map(acc::add));
		}
		return renamed.map(conjuncts -> new Nogood(conjuncts, surfaceOf(conjuncts)));
	}

	@Override
	public String toString() {
		return forbidden.map(c -> "¬(" + c + ")").mkString(" ∧ ");
	}
}
