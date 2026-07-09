package com.tgac.logic.ckanren.store;

// ABOUTME: The chain-inclusive watch matcher: does a change to one term affect a
// ABOUTME: watched term? Shared by store implementations; walks every chain node.

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
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

	/**
	 * Chain matching only: a COMPOSITE watched term does not trigger on its
	 * members' bindings here — in practice propagators watch variables. Watchers
	 * of structures (suspensions over tuples/lists) use
	 * {@link #matchesStructurally}.
	 */
	public static boolean matches(Substitutions state, Term<?> watched, Term<?> changed) {
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
			Term<?> next = state.binding(cur.asVar().get());
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

	/**
	 * Chain matching, recursing into structure: when a watched chain ends in a
	 * composite, its members are watched too — including members that only came
	 * into existence through nested instantiation (watch {@code (a,b)}, bind
	 * {@code a} to {@code (c,d)}: {@code c} now triggers).
	 */
	public static boolean matchesStructurally(Substitutions state, Term<?> watched, Term<?> changed) {
		if (matches(state, watched, changed)) {
			return true;
		}
		Term<?> end = state.walk(watched);
		if (!end.asVal().isDefined()) {
			return false;
		}
		Object v = end.get();
		return MiniKanren.asIterable(v)
				.orElse(() -> MiniKanren.tupleAsIterable(v))
				.map(it -> StreamSupport.stream(it.spliterator(), false)
						.map(MiniKanren::wrapTerm)
						.anyMatch(m -> matchesStructurally(state, m, changed)))
				.getOrElse(() -> MiniKanren.asLList(end)
						.map(l -> l.stream().anyMatch(e -> e.fold(
								m -> matchesStructurally(state, m, changed),
								m -> matchesStructurally(state, m, changed))))
						.getOrElse(false));
	}
}
