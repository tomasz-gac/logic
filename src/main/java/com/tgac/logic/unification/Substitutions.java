package com.tgac.logic.unification;

// ABOUTME: The substitution factor as a first-class read-only view — what code scoped
// ABOUTME: to shared knowledge (suspension conditions) may see: bindings, nothing else.

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import java.util.ArrayDeque;

/**
 * A read-only view of the substitution — the shared factor of the {@link Package}
 * — with no route to any store. Code typed against this view is structurally
 * scoped to shared knowledge: it cannot depend on domains, records or any other
 * private factor (the constraint-kernel.md {@code Substitutions}
 * sketch, finally realized where it has a job).
 */
public final class Substitutions {

	private final HashMap<LVar<?>, Term<?>> bindings;

	Substitutions(HashMap<LVar<?>, Term<?>> bindings) {
		this.bindings = bindings;
	}

	/** One chain step: the term bound to {@code v}, or null when unbound. */
	public Term<?> binding(LVar<?> v) {
		return bindings.getOrElse(v, null);
	}

	/** The term's walk-chain end: a value, or the representative unbound variable. */
	public <T> Term<T> walk(Term<T> t) {
		return Package.of(bindings, LinkedHashMap.empty()).walk(t);
	}

	/** The term deep-walked to its current bindings. */
	public <T> Term<T> walkAll(Term<T> t) {
		return MiniKanren.walkAll(Package.of(bindings, LinkedHashMap.empty()), t).get();
	}

	/**
	 * Whether the term is deep-ground under the current bindings — no variable
	 * remains anywhere in its structure. Heap-stacked: term depth never touches
	 * the JVM stack.
	 */
	public boolean isGround(Term<?> t) {
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.add(walkAll(t));
		while (!pending.isEmpty()) {
			Term<?> cur = pending.poll();
			if (cur.asVar().isDefined()) {
				return false;
			}
			MiniKanren.members(cur)
					.forEach(members -> members.forEach(pending::add));
		}
		return true;
	}
}
