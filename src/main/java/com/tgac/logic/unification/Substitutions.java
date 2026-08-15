package com.tgac.logic.unification;

// ABOUTME: The substitution factor as a first-class read-only view — what code scoped
// ABOUTME: to shared knowledge (suspension conditions) may see: bindings, nothing else.

import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A read-only view of the substitution — the shared factor of the package
 * product — with no route to any store. Code typed against this view is structurally
 * scoped to shared knowledge: it cannot depend on domains, records or any other
 * private factor (the constraint-kernel.md {@code Substitutions}
 * sketch, finally realized where it has a job).
 *
 * <p>Ordered by information (more bindings = more specific), substitutions form
 * a bounded semilattice (combine = join): ⊥ is empty, and the JOIN
 * is UNIFICATION — the least substitution more specific than both. There is no
 * ⊤ value; a clash is failure-as-absence elsewhere (see {@code Absorbing}), so
 * {@link #join} is defined on compatible substitutions and throws otherwise.
 */
public final class Substitutions implements Semilattice<Substitutions> {

	private final HashMap<Unknown<?>, Term<?>> bindings;

	Substitutions(HashMap<Unknown<?>, Term<?>> bindings) {
		this.bindings = bindings;
	}

	public static Substitutions empty() {
		return new Substitutions(HashMap.empty());
	}

	/** A view over an existing binding map — map-level threading (trial unification). */
	public static Substitutions of(HashMap<Unknown<?>, Term<?>> bindings) {
		return new Substitutions(bindings);
	}

	/** This plus one binding — the unifier's extension step. */
	public Substitutions extend(LVar<?> v, Term<?> t) {
		// the unifier's entry: only LIVE vars are ever BOUND — canonical
		// names enter the map as renaming seeds, never through unification
		return new Substitutions(bindings.put(v, t));
	}

	/**
	 * Unification as the lattice join: the least substitution more specific than
	 * both. Throws when they clash — the join view is total,
	 * but a clash has no ⊤ VALUE here (see {@link #tryJoin}), so this partial
	 * function is defined only on compatible substitutions.
	 */
	@Override
	public Substitutions combine(Substitutions other) {
		return join(other);
	}

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
		for (Tuple2<Unknown<?>, Term<?>> binding : other.bindings) {
			Option<Substitutions> step = unifyInto(acc, binding._1, binding._2);
			if (step.isEmpty()) {
				return Option.none();
			}
			acc = step.get();
		}
		return Option.some(acc);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Option<Substitutions> unifyInto(Substitutions acc, Unknown<?> v, Term<?> t) {
		return Unsafe.unify(acc, (Term) v, (Term) t);
	}

	/**
	 * The bindings factor examining an arrived delta — the asserted-prefix
	 * trichotomy, owned by the factor it extends: a pair for a still-open
	 * variable binds its walked representative, one bound to the same value
	 * drops, one bound to a DIFFERENT value is a contradiction between
	 * constraint domains — none, the branch dies. Some carries the extended
	 * factor and the KEPT delta the driver fans out to the other stores
	 * (empty kept = nothing new, a no-op arrival).
	 */
	public Option<Tuple2<Substitutions, Prefix>> extended(Prefix delta) {
		return delta.revalidate(this)
				.map(kept -> Tuple.of(kept.isEmpty() ? this : kept.appliedTo(this), kept));
	}

	/** The number of bindings. Reified variable numbering derives from it. */
	public long size() {
		return bindings.size();
	}

	HashMap<Unknown<?>, Term<?>> map() {
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

	/** The raw binding map, read-only — vavr, so sharing it is safe. */
	public HashMap<Unknown<?>, Term<?>> bindings() {
		return bindings;
	}

	/** One chain step: the term bound to {@code v}, or null when unbound. */
	public Term<?> binding(Unknown<?> v) {
		return bindings.getOrElse(v, null);
	}

	/** The term's walk-chain end: a value, or the representative unbound variable. */
	@SuppressWarnings("unchecked")
	public <T> Term<T> walk(Term<T> v) {
		if (!v.asUnknown().isDefined()) {
			return v;
		}
		Term<?> result = v;
		Term<?> next;
		while (result.asUnknown().isDefined()
				&& (next = bindings.getOrElse(result.asUnknown().get(), null)) != null) {
			result = next;
		}
		return (Term<T>) result;
	}

	/** The term deep-walked to its current bindings. */
	public <T> Fiber<Term<T>> walkAll(Term<T> t) {
		return MiniKanren.walkAll(this, t);
	}

	/**
	 * The unknowns still free in {@code t} under the current bindings —
	 * {@link MiniKanren#namesIn}'s traversal taken through the walk, without
	 * building the deep-walked copy.
	 */
	public Stream<Unknown<?>> namesIn(Term<?> t) {
		ArrayDeque<Term<?>> work = new ArrayDeque<>();
		work.push(t);
		return StreamSupport.stream(new Spliterators.AbstractSpliterator<Unknown<?>>(
				Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
			@Override
			public boolean tryAdvance(Consumer<? super Unknown<?>> action) {
				while (!work.isEmpty()) {
					Term<?> current = walk(work.pop());
					if (current.asUnknown().isDefined()) {
						action.accept(current.asUnknown().get());
						return true;
					}
					MiniKanren.members(current).forEach(members -> members.forEach(work::push));
				}
				return false;
			}
		}, false);
	}

	/**
	 * Whether the term is deep-ground under the current bindings — no variable
	 * remains anywhere in its structure. Heap-stacked: term depth never touches
	 * the JVM stack.
	 */
	public boolean isGround(Term<?> t) {
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.add(t);
		while (!pending.isEmpty()) {
			Term<?> cur = walk(pending.poll());
			if (cur.asVar().isDefined()) {
				return false;
			}
			MiniKanren.members(cur)
					.forEach(members -> members.forEach(pending::add));
		}
		return true;
	}
}
