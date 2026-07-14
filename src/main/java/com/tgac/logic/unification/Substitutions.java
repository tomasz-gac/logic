package com.tgac.logic.unification;

// ABOUTME: The substitution factor as a first-class read-only view — what code scoped
// ABOUTME: to shared knowledge (suspension conditions) may see: bindings, nothing else.

import com.tgac.functional.algebra.JoinSemilattice;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.util.ArrayDeque;

/**
 * A read-only view of the substitution — the shared factor of the package
 * product — with no route to any store. Code typed against this view is structurally
 * scoped to shared knowledge: it cannot depend on domains, records or any other
 * private factor (the constraint-kernel.md {@code Substitutions}
 * sketch, finally realized where it has a job).
 *
 * <p>Ordered by information (more bindings = more specific), substitutions form
 * a bounded {@link JoinSemilattice join-semilattice}: ⊥ is empty, and the JOIN
 * is UNIFICATION — the least substitution more specific than both. There is no
 * ⊤ value; a clash is failure-as-absence elsewhere (see {@code Bottomed}), so
 * {@link #join} is defined on compatible substitutions and throws otherwise.
 */
public final class Substitutions implements JoinSemilattice<Substitutions> {

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

	/**
	 * Unification as the lattice join: the least substitution more specific than
	 * both. Throws when they clash — the {@link JoinSemilattice} view is total,
	 * but a clash has no ⊤ VALUE here (see {@link #tryJoin}), so this partial
	 * function is defined only on compatible substitutions.
	 */
	@Override
	public Substitutions join(Substitutions other) {
		return tryJoin(other).getOrElseThrow(() -> new IllegalStateException(
				"join of incompatible substitutions"));
	}

	/**
	 * The join made total by ABSENCE: {@code none} is ⊤ (the clash), represented
	 * the way the CPS engine represents all failure — as absence, not a value.
	 * This is the ⊤-aware form; {@code none} is the top singleton.
	 */
	public Option<Substitutions> tryJoin(Substitutions other) {
		Substitutions acc = this;
		for (Tuple2<LVar<?>, Term<?>> binding : other.bindings) {
			Option<Substitutions> step = unifyInto(acc, binding._1, binding._2);
			if (step.isEmpty()) {
				return Option.none();
			}
			acc = step.get();
		}
		return Option.some(acc);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Option<Substitutions> unifyInto(Substitutions acc, LVar<?> v, Term<?> t) {
		return MiniKanren.unify(acc, (Term) v, (Term) t).get();
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
