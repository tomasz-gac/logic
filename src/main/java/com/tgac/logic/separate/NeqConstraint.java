package com.tgac.logic.separate;

import com.tgac.logic.unification.LVar;
import com.tgac.logic.goals.Store;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraint implements Stored {
	HashMap<LVar<?>, Term<?>> separate;

	@Override
	public Class<? extends Store> getStoreClass() {
		return NeqConstraints.class;
	}
}
