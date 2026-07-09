package com.tgac.logic.unification;

// ABOUTME: The substitution factor as a first-class read-only view — what code scoped
// ABOUTME: to shared knowledge (suspension conditions) may see: bindings, nothing else.

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;

/**
 * A read-only view of the substitution — the shared factor of the {@link Package}
 * — with no route to any store. Code typed against this view is structurally
 * scoped to shared knowledge: it cannot depend on domains, records or any other
 * private factor (the capability-constraint-api.md §2.1 {@code Substitutions}
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

	/** Whether the term is deep-ground under the current bindings — no variable
	 * remains anywhere in its structure. */
	public boolean isGround(Term<?> t) {
		return fullyGround(walkAll(t));
	}

	private static boolean fullyGround(Term<?> t) {
		if (t.asVar().isDefined()) {
			return false;
		}
		Object v = t.get();
		return MiniKanren.asIterable(v)
				.orElse(() -> MiniKanren.tupleAsIterable(v))
				.map(it -> {
					for (Object o : it) {
						if (!fullyGround(MiniKanren.wrapTerm(o))) {
							return false;
						}
					}
					return true;
				})
				.getOrElse(() -> MiniKanren.asLList(t)
						.map(l -> l.stream().allMatch(e -> e.fold(
								Substitutions::fullyGround,
								Substitutions::fullyGround)))
						.getOrElse(true));
	}
}
