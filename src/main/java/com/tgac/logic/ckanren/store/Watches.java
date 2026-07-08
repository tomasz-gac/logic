package com.tgac.logic.ckanren.store;

// ABOUTME: The chain-inclusive watch matcher: does a change to one term affect a
// ABOUTME: watched term? Shared by store implementations; walks every chain node.

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import java.util.stream.StreamSupport;

/**
 * Watch matching against the LIVE state: the changed term may be the watched
 * term itself, an alias link in the middle of its walk chain, or the end of the
 * chain — a full walk would step THROUGH a just-bound variable to its value and
 * miss the match, so every chain node is checked. A ground composite matches
 * element-wise (enforcement pokes with answer terms).
 */
public final class Watches {

	private Watches() {
	}

	public static boolean matches(Package state, Term<?> watched, Term<?> changed) {
		Term<?> cur = watched;
		while (true) {
			if (cur.equals(changed)) {
				return true;
			}
			if (changed.isVal() && changedContains(changed, cur)) {
				return true;
			}
			if (!cur.asVar().isDefined()) {
				return false;
			}
			Term<?> next = state.getSubstitutions().getOrElse(cur.asVar().get(), null);
			if (next == null) {
				return false;
			}
			cur = next;
		}
	}

	private static boolean changedContains(Term<?> changed, Term<?> live) {
		Object w = changed.get();
		return MiniKanren.asIterable(w)
				.orElse(() -> MiniKanren.tupleAsIterable(w))
				.map(it -> StreamSupport.stream(it.spliterator(), false)
						.map(MiniKanren::wrapTerm)
						.anyMatch(live::equals))
				.getOrElse(() -> MiniKanren.asLList(changed)
						.map(l -> l.stream()
								.anyMatch(e -> e.fold(live::equals, live::equals)))
						.getOrElse(false));
	}
}
