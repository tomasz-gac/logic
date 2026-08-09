package com.tgac.logic.notes;

// ABOUTME: One note: NOT all these postings simultaneously — Neq's record shape
// ABOUTME: with postings as the pairs. Born as its escape list; nothing converts.

import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.List;
import java.util.stream.Stream;
import lombok.Value;

/**
 * {@code ¬(p₁ ∧ … ∧ pₙ)}: at least one posting must fail to hold. Read as its
 * escape list directly — each posting is one escape, the four moves consume
 * them in place. The store-level conjunction of many notes is the package's;
 * a note only ever says "not all of these".
 */
@Value(staticConstructor = "of")
public class Note implements Stored {
	List<Posting> postings;

	@Override
	public Class<? extends Store> getStoreClass() {
		throw new UnsupportedOperationException(
				"the note store is not built yet: the verification core precedes it");
	}

	@Override
	public Stream<Term<?>> terms() {
		return postings.toJavaStream().flatMap(Posting::terms);
	}

	@Override
	public String toString() {
		return postings.mkString("¬(", " ∧ ", ")");
	}
}
