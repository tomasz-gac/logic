package com.tgac.logic.separate;

import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraint implements Stored {
	/** NOT all these bindings simultaneously — keyed by NAME (live var or hole). */
	HashMap<Term<?>, Term<?>> separate;

	@Override
	public Class<? extends Store> getStoreClass() {
		return NeqConstraints.class;
	}

	@Override
	public Stream<Term<?>> terms() {
		return separate.toJavaStream().flatMap(binding -> Stream.of(binding._1, binding._2));
	}
}
