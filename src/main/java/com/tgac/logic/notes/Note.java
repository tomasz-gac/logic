package com.tgac.logic.notes;

// ABOUTME: One note: "at least one escape must hold" — a disjunction of negative
// ABOUTME: box literals, each (anchor, box) read as anchor ∉ box.

import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class Note implements Stored {
	/** The escapes: anchor ∉ box each; the note holds while at least one can. */
	List<Tuple2<Term<?>, Domain<?>>> escapes;

	@Override
	public Class<? extends Store> getStoreClass() {
		return NoteStore.class;
	}

	@Override
	public Stream<Term<?>> terms() {
		return escapes.toJavaStream().map(Tuple2::_1);
	}
}
