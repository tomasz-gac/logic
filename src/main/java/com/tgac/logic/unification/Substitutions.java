package com.tgac.logic.unification;

// ABOUTME: The substitution factor as a first-class read-only view — what code scoped
// ABOUTME: to shared knowledge (suspension conditions) may see: bindings, nothing else.

import io.vavr.collection.HashMap;
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

	public static Substitutions empty() {
		return new Substitutions(HashMap.empty());
	}

	/** A view over an existing binding map — map-level threading (trial unification). */
	public static Substitutions of(HashMap<LVar<?>, Term<?>> bindings) {
		return new Substitutions(bindings);
	}

	/** This plus one binding — the unifier's extension step. */
	public Substitutions extend(LVar<?> v, Term<?> t) {
		return new Substitutions(bindings.put(v, t));
	}

	/** The number of bindings. Reified variable numbering derives from it. */
	public long size() {
		return bindings.size();
	}

	HashMap<LVar<?>, Term<?>> map() {
		return bindings;
	}

	public boolean isEmpty() {
		return bindings.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		// representation-independent: two substitutions are equal iff their
		// bindings are — the contract any future backing must keep
		return o instanceof Substitutions && bindings.equals(((Substitutions) o).bindings);
	}

	@Override
	public int hashCode() {
		return bindings.hashCode();
	}

	@Override
	public String toString() {
		return bindings.toString();
	}

	/** One chain step: the term bound to {@code v}, or null when unbound. */
	public Term<?> binding(LVar<?> v) {
		return bindings.getOrElse(v, null);
	}

	/** The term's walk-chain end: a value, or the representative unbound variable. */
	@SuppressWarnings("unchecked")
	public <T> Term<T> walk(Term<T> v) {
		// same loop as Package.walk, duplicated to keep both hot paths allocation-free
		if (!v.asVar().isDefined()) {
			return v;
		}
		Term<?> result = v;
		Term<?> next;
		while (result.asVar().isDefined()
				&& (next = bindings.getOrElse(result.asVar().get(), null)) != null) {
			result = next;
		}
		return (Term<T>) result;
	}

	/** The term deep-walked to its current bindings. */
	public <T> Term<T> walkAll(Term<T> t) {
		return MiniKanren.walkAll(this, t).get();
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
