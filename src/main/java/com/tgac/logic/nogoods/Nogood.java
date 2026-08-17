package com.tgac.logic.nogoods;

// ABOUTME: One nogood: NOT this posting — the Stored envelope that routes a
// ABOUTME: forbidden conjunct to the NogoodConstraints store; the ∧ lives in Posting.all.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Transcribable;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.unification.Term;
import io.vavr.collection.List;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;

/**
 * {@code ¬(forbidden)}: the envelope only says "this posting belongs to the
 * NogoodConstraints store and is read negatively" — the conjunction, its jointness and
 * its literal granularity all live in the posting itself ({@link Posting#all}
 * for {@code ¬(l₁ ∧ … ∧ lₙ)}). The store-level conjunction of many nogoods
 * is the package's; a nogood only ever says "not this".
 */
@Value
public class Nogood implements Atom<NogoodConstraints>, Transcribable<Nogood> {
	Posting forbidden;

	/**
	 * The forbidden conjunct is held FLAT: ∧ is associative, so nested
	 * {@code all}s are one conjunction — flattening at the envelope makes
	 * structural equality match semantic equality (dedup and cross-lineage
	 * key comparison would otherwise miss same-content nogoods that differ
	 * only in nesting) and keeps the trial's fast-path partition shallow.
	 * Labels unwrap on the way (presentation, outside identity).
	 */
	public static Nogood of(Posting forbidden) {
		List<Posting> flat = forbidden.accept(FLATTEN);
		return new Nogood(flat.size() == 1 ?
				flat.head() :
				Posting.all(flat.toJavaArray(Posting[]::new)));
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

	@Override
	public Class<? extends NogoodConstraints> getFactorClass() {
		return NogoodConstraints.class;
	}

	@Override
	public String name() {
		return "nogood";
	}

	@Override
	public Stream<Term<?>> watched() {
		return forbidden.terms();
	}

	@Override
	public Object payload() {
		return forbidden;
	}

	/**
	 * Nogood subsumption: ¬(A) entails ¬(A ∧ B) — this ⊑ other iff this
	 * forbidden conjunction is a SUBSET of the other's (flattened; literal
	 * equality is structural, so sharp only over walked literals).
	 */
	@Override
	public boolean leq(Atom<NogoodConstraints> other) {
		if (!(other instanceof Nogood)) {
			return equals(other);
		}
		Set<Posting> mine = literalSet(this);
		return literalSet((Nogood) other).containsAll(mine);
	}

	private static Set<Posting> literalSet(Nogood nogood) {
		return (nogood.forbidden instanceof Posting.AllOf ?
				((Posting.AllOf) nogood.forbidden).getParts().toJavaList() :
				Collections.singletonList(nogood.forbidden))
				.stream().collect(Collectors.toSet());
	}

	/** The posting transcribes itself wrapped; the envelope follows. */
	@Override
	public Fiber<Nogood> rename(Renaming renaming) {
		return forbidden.rename(renaming).map(Nogood::of);
	}

	@Override
	public String toString() {
		return "¬(" + forbidden + ")";
	}
}
