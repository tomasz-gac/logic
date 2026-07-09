package com.tgac.logic.ckanren.store;

// ABOUTME: The chain-inclusive watch matcher: does a change to one term affect a
// ABOUTME: watched term? Shared by store implementations; walks every chain node.

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import java.util.ArrayDeque;

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

	/**
	 * Chain matching over the whole structure: a watched chain ending in a
	 * composite watches its members too — including members that only came into
	 * existence through nested instantiation (watch {@code (a,b)}, bind
	 * {@code a} to {@code (c,d)}: {@code c} now triggers). Structure is whatever
	 * the unifier's own decomposition recognizes ({@code MiniKanren.members}:
	 * collections, tuples, LList, LTree). Heap-stacked: term depth never touches
	 * the JVM stack.
	 */
	public static boolean matchesStructurally(Substitutions state, Term<?> watched, Term<?> changed) {
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.add(watched);
		while (!pending.isEmpty()) {
			Term<?> cur = pending.poll();
			if (matches(state, cur, changed)) {
				return true;
			}
			MiniKanren.members(state.walk(cur))
					.forEach(members -> members.forEach(pending::add));
		}
		return false;
	}

	private static boolean changedContains(Term<?> changed, Term<?> live) {
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		MiniKanren.members(changed).forEach(members -> members.forEach(pending::add));
		while (!pending.isEmpty()) {
			Term<?> cur = pending.poll();
			if (cur.equals(live)) {
				return true;
			}
			MiniKanren.members(cur).forEach(members -> members.forEach(pending::add));
		}
		return false;
	}
}
