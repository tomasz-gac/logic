package com.tgac.logic.goals;

import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Name;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {

	Substitutions substitutions;

	LinkedHashMap<Class<? extends Packaged>, Packaged> stores;

	public static Package empty() {
		return Package.of(Substitutions.empty(), LinkedHashMap.empty());
	}

	public static Package of(HashMap<Name<?>, Term<?>> substitutions,
			LinkedHashMap<Class<? extends Packaged>, Packaged> stores) {
		return Package.of(Substitutions.of(substitutions), stores);
	}

	public Package withSubstitutions(Substitutions s) {
		return Package.of(s, stores);
	}

	/** Renders a value for a trace label — a {@link Term} is deep-walked to its current bindings. */
	public String format(Object o) {
		return MiniKanren.format(substitutions, o);
	}

	public <T> Term<T> walk(Term<T> v) {
		return substitutions.walk(v);
	}

	/** The substitution factor — see {@link Substitutions}. */
	public Substitutions substitution() {
		return substitutions;
	}

	public long size() {
		return substitutions.size();
	}

	public Package withStore(Packaged empty) {
		if (stores.get(empty.getClass()).isDefined()) {
			return this;
		} else {
			return Package.of(substitutions, stores.put(empty.getClass(), entryOf(empty)));
		}
	}

	public Package putStore(Packaged store) {
		return Package.of(substitutions, stores.put(store.getClass(), entryOf(store)));
	}

	/**
	 * A factor enters as its {@link Constraint} pair — knowledge beside its
	 * interpreter, keyed by the family class; luggage rides plain.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Packaged entryOf(Packaged store) {
		return store instanceof Factor
				? Constraint.of(((Factor) store).theory(), (Factor) store)
				: store;
	}

	public Package withoutStore(Class<? extends Packaged> cls) {
		return Package.of(substitutions, stores.remove(cls));
	}

	/** The payload registered under {@code cls}; throws when absent. */
	@SuppressWarnings("unchecked")
	public <T extends Packaged> T getStore(Class<T> cls) {
		Packaged entry = stores.get(cls)
				.getOrElseThrow(() -> new IllegalStateException(
						"No store associated with package"));
		return (T) (entry instanceof Constraint ? ((Constraint<?>) entry).getFactor() : entry);
	}

	/** Prepends {@code c} into its store; unchanged when the store is absent. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Package withStored(Atom<?> c) {
		Watermark.check(this, c);
		Class<? extends Packaged> key = c.getFactorClass();
		return stores.get(key)
				.map(cs -> (Packaged) ((Constraint) cs).getFactor().meet(c))
				.map(cs -> Package.of(substitutions, stores.put(key, entryOf(cs))))
				.getOrElse(this);
	}


	/** Applies {@code f} to the payload registered under {@code cls}; unchanged when absent. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public <T extends Packaged> Package updateStore(Class<T> cls, UnaryOperator<T> f) {
		return stores.get(cls)
				.map(s -> s instanceof Constraint ? (T) ((Constraint) s).getFactor() : (T) s)
				.map(s -> (Packaged) f.apply(s))
				.map(s -> Package.of(substitutions, stores.put(cls, entryOf(s))))
				.getOrElse(this);
	}
}
