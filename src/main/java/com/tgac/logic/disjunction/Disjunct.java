package com.tgac.logic.disjunction;

// ABOUTME: One disjunct: at least ONE of these postings must hold — the Stored
// ABOUTME: envelope routing alternatives to the DisjunctionConstraints store.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Transcribable;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.List;
import java.util.stream.Stream;
import lombok.Value;

/**
 * {@code a₁ ∨ … ∨ aₙ}: the envelope only says "these postings belong to the
 * DisjunctionConstraints store and at least one must eventually hold". Each
 * alternative is an independent hypothesis — a conjunctive alternative uses
 * {@link Posting#all} and threads internally, but alternatives never thread
 * into each other: they are rival worlds, tried against the same base.
 */
@Value
public class Disjunct implements Stored, Transcribable {
	List<Posting> alternatives;

	/**
	 * Held FLAT: ∨ is associative, so a nested {@code anyOf} splices its
	 * alternatives into the enclosing disjunct — structural equality matches
	 * semantic equality, and unit detection counts real alternatives, not
	 * nesting artifacts. Labels unwrap on the way (presentation, outside
	 * identity).
	 */
	public static Disjunct of(Posting... alternatives) {
		return new Disjunct(flatten(alternatives));
	}

	static List<Posting> flatten(Posting... alternatives) {
		return List.of(alternatives).flatMap(a -> a.accept(SPLICE));
	}

	private static final Posting.Visitor<List<Posting>> SPLICE =
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
					return activation.getItem() instanceof Disjunct ?
							((Disjunct) activation.getItem()).getAlternatives() :
							List.of(activation);
				}

				@Override
				public List<Posting> visit(Posting.Absorption absorption) {
					return List.of(absorption);
				}

				@Override
				public List<Posting> visit(Posting.AllOf all) {
					return List.of(all);
				}
			};

	@Override
	public Class<? extends Store> getStoreClass() {
		return DisjunctionConstraints.class;
	}

	@Override
	public Stream<Term<?>> terms() {
		return alternatives.toJavaStream().flatMap(Posting::terms);
	}

	@Override
	public Fiber<Stored> rename(Renaming renaming) {
		return alternatives.foldLeft(
						Fiber.<List<Posting>> done(List.empty()),
						(acc, alternative) -> acc.flatMap(renamed ->
								alternative.rename(renaming).map(renamed::append)))
				.map(Disjunct::new);
	}

	@Override
	public String toString() {
		return "(" + alternatives.mkString(" ∨ ") + ")";
	}
}
