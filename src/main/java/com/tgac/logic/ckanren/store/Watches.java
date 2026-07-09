package com.tgac.logic.ckanren.store;

// ABOUTME: The chain-inclusive watch matcher: does a change to one term affect a
// ABOUTME: watched term? Shared by store implementations; walks every chain node.

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import java.util.function.Predicate;

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
	 * Chain matching, recursing into structure: when a watched chain ends in a
	 * composite, its members are watched too — including members that only came
	 * into existence through nested instantiation (watch {@code (a,b)}, bind
	 * {@code a} to {@code (c,d)}: {@code c} now triggers). Structure is whatever
	 * the unifier's own decomposition recognizes ({@code MiniKanren.members}:
	 * collections, tuples, LList, LTree) — the two traversals cannot drift apart.
	 */
	public static boolean matchesStructurally(Substitutions state, Term<?> watched, Term<?> changed) {
		if (matches(state, watched, changed)) {
			return true;
		}
		Term<?> end = state.walk(watched);
		return structuralAny(end, member -> matchesStructurally(state, member, changed));
	}

	/** Any structural member (per the unifier's decomposition) satisfying {@code p}. */
	private static boolean structuralAny(Term<?> t, Predicate<Term<?>> p) {
		return MiniKanren.members(t)
				.map(members -> {
					for (Term<?> member : members) {
						if (p.test(member)) {
							return true;
						}
					}
					return false;
				})
				.getOrElse(false);
	}

	private static boolean changedContains(Term<?> changed, Term<?> live) {
		return structuralAny(changed, member -> member.equals(live) || changedContains(member, live));
	}
}
