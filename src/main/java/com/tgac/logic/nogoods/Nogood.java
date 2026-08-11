package com.tgac.logic.nogoods;

// ABOUTME: One nogood: NOT all these literals simultaneously — Neq's record shape
// ABOUTME: with literals as the pairs. Born as its escape list; nothing converts.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Transcribable;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.List;
import java.util.stream.Stream;
import lombok.Value;

/**
 * {@code ¬(p₁ ∧ … ∧ pₙ)}: at least one literal must fail to hold. Read as its
 * escape list directly — each literal is one escape, the four moves consume
 * them in place. The store-level conjunction of many nogoods is the package's;
 * a nogood only ever says "not all of these".
 */
@Value(staticConstructor = "of")
public class Nogood implements Stored, Transcribable {
	List<Posting> literals;

	@Override
	public Class<? extends Store> getStoreClass() {
		return Nogoods.class;
	}

	@Override
	public Stream<Term<?>> terms() {
		return literals.toJavaStream().flatMap(Posting::terms);
	}

	/** Every literal transcribed wrapped — the posting rows answer for themselves. */
	@Override
	public Fiber<Stored> rename(Renaming renaming) {
		return literals.foldLeft(
						Fiber.<List<Posting>> done(List.empty()),
						(acc, literal) -> acc.flatMap(renamed ->
								literal.rename(renaming).map(renamed::append)))
				.map(renamed -> Nogood.of(renamed));
	}

	@Override
	public String toString() {
		return literals.mkString("¬(", " ∧ ", ")");
	}
}
