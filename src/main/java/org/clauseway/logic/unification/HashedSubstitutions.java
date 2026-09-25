package org.clauseway.logic.unification;

// ABOUTME: The hashed representation of Substitutions: bindings in a persistent
// ABOUTME: vavr HAMT keyed by name identity — the baseline any new backing must beat.

import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.vavr.collection.HashMap;
import java.util.Map;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Name;
import org.clauseway.logic.unification.terms.Term;

final class HashedSubstitutions implements Substitutions {

	static final HashedSubstitutions EMPTY = new HashedSubstitutions(HashMap.empty());

	private final HashMap<Name<?>, Term<?>> bindings;

	private HashedSubstitutions(HashMap<Name<?>, Term<?>> bindings) {
		this.bindings = bindings;
	}

	static HashedSubstitutions of(Map<? extends Name<?>, ? extends Term<?>> bindings) {
		return new HashedSubstitutions(HashMap.ofAll(bindings));
	}

	@Override
	public Term<?> binding(Name<?> v) {
		return bindings.getOrElse(v, null);
	}

	@Override
	public Substitutions extend(LVar<?> v, Term<?> t) {
		// the unifier's entry: only LIVE vars are ever BOUND — canonical
		// names enter the map as renaming seeds, never through unification
		return new HashedSubstitutions(bindings.put(v, t));
	}

	@Override
	public int size() {
		return bindings.size();
	}

	@Override
	public boolean isEmpty() {
		return bindings.isEmpty();
	}

	@Override
	public Iterable<Tuple2<Name<?>, Term<?>>> bindings() {
		return () -> bindings.iterator()
				.map(entry -> Tuple.<Name<?>, Term<?>> of(entry._1, entry._2));
	}

	/** The interface default specialized onto the map — the walk loop stays direct. */
	@SuppressWarnings("unchecked")
	@Override
	public <T> Term<T> walk(Term<T> v) {
		if (!v.asName().isPresent()) {
			return v;
		}
		Term<?> result = v;
		Term<?> next;
		while (result.asName().isPresent()
				&& (next = bindings.getOrElse(result.asName().get(), null)) != null) {
			result = next;
		}
		return (Term<T>) result;
	}

	@Override
	public boolean equals(Object o) {
		// representation-independent: two substitutions are equal iff their
		// bindings are — the contract any backing must keep
		if (o instanceof HashedSubstitutions) {
			return bindings.equals(((HashedSubstitutions) o).bindings);
		}
		if (!(o instanceof Substitutions)) {
			return false;
		}
		Substitutions other = (Substitutions) o;
		if (other.size() != size()) {
			return false;
		}
		for (Tuple2<Name<?>, Term<?>> binding : other.bindings()) {
			if (!binding._2.equals(binding(binding._1))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		return bindings.hashCode();
	}

	@Override
	public String toString() {
		return bindings.toString();
	}
}
