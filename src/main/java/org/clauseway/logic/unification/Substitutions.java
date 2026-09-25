package org.clauseway.logic.unification;

// ABOUTME: The substitution factor as a first-class read-only view — what code scoped
// ABOUTME: to shared knowledge may see; an interface so the representation can swap.

import org.clauseway.functional.algebra.Semilattice;
import org.clauseway.functional.tuples.Tuple2;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Name;
import org.clauseway.logic.unification.terms.Term;

/**
 * A read-only view of the substitution — the shared factor of the package
 * product — with no route to any store. Code typed against this view is structurally
 * scoped to shared knowledge: it cannot depend on domains, records or any other
 * private factor (the constraint-kernel.md {@code Substitutions}
 * sketch, finally realized where it has a job).
 *
 * <p>The primitive core is four operations — {@link #binding}, {@link #extend},
 * {@link #size}, {@link #bindings} — and everything else is derived, so a
 * representation implements the core and is correct. Contracts every backing
 * must keep: equality is the binding SET, independent of representation
 * (packages are values over this); {@code size()} is the number of bindings,
 * because reified variable numbering ({@code _.0}, {@code _.1}, …) derives
 * from it; and no operation removes a binding — backtracking is a different
 * package, never a retraction. One representation per solve: joins meet
 * same-representation values, the derived fallbacks only cover the rest.
 *
 * <p>Ordered by information (more bindings = more specific), substitutions form
 * a bounded semilattice (combine = join): ⊥ is empty, and the JOIN
 * is UNIFICATION — the least substitution more specific than both. There is no
 * ⊤ value; a clash is failure-as-absence elsewhere (see {@code Absorbing}), so
 * {@link #join} is defined on compatible substitutions and throws otherwise.
 */
public interface Substitutions extends Semilattice<Substitutions> {

	static Substitutions empty() {
		return HashedSubstitutions.EMPTY;
	}

	/** A view over existing bindings — map-level threading (trial unification, renaming seeds). */
	static Substitutions of(Map<? extends Name<?>, ? extends Term<?>> bindings) {
		return HashedSubstitutions.of(bindings);
	}

	/**
	 * One chain step: the term bound to {@code v}, or null when unbound. The
	 * walk loop's body — null over Optional keeps the hottest call in the
	 * engine allocation-free.
	 */
	Term<?> binding(Name<?> v);

	/** This plus one binding — the unifier's extension step. */
	Substitutions extend(LVar<?> v, Term<?> t);

	/** The number of bindings. Reified variable numbering derives from it. */
	long size();

	/** The bindings as pairs, in the representation's iteration order. */
	Iterable<Tuple2<Name<?>, Term<?>>> bindings();

	default boolean isEmpty() {
		return size() == 0;
	}

	/** The bindings copied into a plain map, in iteration order. */
	default Map<Name<?>, Term<?>> toMap() {
		Map<Name<?>, Term<?>> out = new LinkedHashMap<>();
		for (Tuple2<Name<?>, Term<?>> binding : bindings()) {
			out.put(binding._1, binding._2);
		}
		return out;
	}

	/** The term's walk-chain end: a value, or the representative unbound variable. */
	@SuppressWarnings("unchecked")
	default <T> Term<T> walk(Term<T> v) {
		if (!v.asName().isPresent()) {
			return v;
		}
		Term<?> result = v;
		Term<?> next;
		while (result.asName().isPresent()
				&& (next = binding(result.asName().get())) != null) {
			result = next;
		}
		return (Term<T>) result;
	}

	/** The term deep-walked to its current bindings. */
	@SuppressWarnings("deprecation")
	default <T> Term<T> walkAll(Term<T> t) {
		return MiniKanren.walkAll(this, t).ground();
	}

	/**
	 * The names still free in {@code t} under the current bindings —
	 * {@link MiniKanren#namesIn}'s traversal taken through the walk, without
	 * building the deep-walked copy.
	 */
	default Stream<Name<?>> namesIn(Term<?> t) {
		ArrayDeque<Term<?>> work = new ArrayDeque<>();
		work.push(t);
		return StreamSupport.stream(new Spliterators.AbstractSpliterator<Name<?>>(
				Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
			@Override
			public boolean tryAdvance(Consumer<? super Name<?>> action) {
				while (!work.isEmpty()) {
					Term<?> current = walk(work.pop());
					if (current.asName().isPresent()) {
						action.accept(current.asName().get());
						return true;
					}
					MiniKanren.members(current).ifPresent(members -> members.forEach(work::push));
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
	default boolean isGround(Term<?> t) {
		ArrayDeque<Term<?>> pending = new ArrayDeque<>();
		pending.add(t);
		while (!pending.isEmpty()) {
			Term<?> cur = walk(pending.poll());
			if (cur.asVar().isPresent()) {
				return false;
			}
			MiniKanren.members(cur)
					.ifPresent(members -> members.forEach(pending::add));
		}
		return true;
	}

	/**
	 * Unification as the lattice join: the least substitution more specific than
	 * both. Throws when they clash — the join view is total,
	 * but a clash has no ⊤ VALUE here (see {@link #tryJoin}), so this partial
	 * function is defined only on compatible substitutions.
	 */
	@Override
	default Substitutions combine(Substitutions other) {
		return join(other);
	}

	default Substitutions join(Substitutions other) {
		return tryJoin(other).getOrElseThrow(() -> new IllegalStateException(
				"join of incompatible substitutions"));
	}

	/**
	 * The join made total by ABSENCE: {@code none} is ⊤ (the clash), represented
	 * the way the CPS engine represents all failure — as absence, not a value.
	 * This is the ⊤-aware form; {@code none} is the top singleton.
	 */
	@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
	default Option<Substitutions> tryJoin(Substitutions other) {
		Substitutions acc = this;
		for (Tuple2<Name<?>, Term<?>> binding : other.bindings()) {
			Option<Substitutions> step =
					MiniKanren.unify(acc, (Term) binding._1, (Term) binding._2).ground();
			if (step.isEmpty()) {
				return Option.none();
			}
			acc = step.get();
		}
		return Option.some(acc);
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
	default Option<io.vavr.Tuple2<Substitutions, Prefix>> extended(Prefix delta) {
		return delta.revalidate(this)
				.map(kept -> io.vavr.Tuple.of(kept.isEmpty() ? this : kept.appliedTo(this), kept));
	}
}
