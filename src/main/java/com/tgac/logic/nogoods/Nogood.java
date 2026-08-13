package com.tgac.logic.nogoods;

// ABOUTME: One nogood: NOT this posting — the Stored envelope that routes a
// ABOUTME: forbidden conjunct to the Nogoods store; the ∧ lives in Posting.all.

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
 * {@code ¬(forbidden)}: the envelope only says "this posting belongs to the
 * Nogoods store and is read negatively" — the conjunction, its jointness and
 * its literal granularity all live in the posting itself ({@link Posting#all}
 * for {@code ¬(l₁ ∧ … ∧ lₙ)}). The store-level conjunction of many nogoods
 * is the package's; a nogood only ever says "not this".
 */
@Value(staticConstructor = "of")
public class Nogood implements Stored, Transcribable {
	Posting forbidden;

	@Override
	public Class<? extends Store> getStoreClass() {
		return Nogoods.class;
	}

	@Override
	public Stream<Term<?>> terms() {
		return forbidden.terms();
	}

	/** The posting transcribes itself wrapped; the envelope follows. */
	@Override
	public Fiber<Stored> rename(Renaming renaming) {
		return forbidden.rename(renaming).map(Nogood::of);
	}

	@Override
	public String toString() {
		return "¬(" + forbidden + ")";
	}
}
