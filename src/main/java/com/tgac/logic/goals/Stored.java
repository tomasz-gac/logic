package com.tgac.logic.goals;

import com.tgac.logic.unification.Term;
import java.util.stream.Stream;

public interface Stored {
	Class<? extends Store> getStoreClass();

	/** Every term this item speaks about — the surface boundary checks scan. */
	Stream<Term<?>> terms();
}
