package org.clauseway.logic.nogoods;

// ABOUTME: Test access for cross-package receipts: build nogood factors and
// ABOUTME: compare them by factor leq and by theory covering.

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.constraints.store.Theory;
import io.vavr.collection.LinkedHashSet;

public final class NogoodTestAccess {
	private NogoodTestAccess() {
	}

	public static Object of(Posting... forbidden) {
		LinkedHashSet<Nogood> nogoods = LinkedHashSet.empty();
		for (Posting posting : forbidden) {
			nogoods = nogoods.add(Nogood.of(posting));
		}
		return Theory.of(nogoods);
	}

	@SuppressWarnings("unchecked")
	public static boolean theoryLeq(Object a, Object b) {
		return ((Theory<NogoodConstraints>) a).leq((Theory<NogoodConstraints>) b);
	}
}
