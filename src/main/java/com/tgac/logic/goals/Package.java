package com.tgac.logic.goals;

import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
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

	public static Package of(HashMap<LVar<?>, Term<?>> substitutions,
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
			return Package.of(substitutions, stores.put(empty.getClass(), empty));
		}
	}

	public Package putStore(Packaged store) {
		return Package.of(substitutions, stores.put(store.getClass(), store));
	}

	public Package withoutStore(Class<? extends Packaged> cls) {
		return Package.of(substitutions, stores.remove(cls));
	}

	/** The payload registered under {@code cls}; throws when absent. */
	@SuppressWarnings("unchecked")
	public <T extends Packaged> T getStore(Class<T> cls) {
		return (T) stores.get(cls)
				.getOrElseThrow(() -> new IllegalStateException(
						"No store associated with package"));
	}

	/** Prepends {@code c} into its store; unchanged when the store is absent. */
	public Package withStored(Stored c) {
		return stores.get(c.getStoreClass())
				.map(cs -> ((Store) cs).prepend(c))
				.map(cs -> Package.of(substitutions, stores.put(c.getStoreClass(), cs)))
				.getOrElse(this);
	}

	/** Removes {@code c} from its store; unchanged when the store is absent. */
	public Package withoutStored(Stored c) {
		return stores.get(c.getStoreClass())
				.map(cs -> ((Store) cs).remove(c))
				.map(cs -> Package.of(substitutions, stores.put(c.getStoreClass(), cs)))
				.getOrElse(this);
	}

	/** Applies {@code f} to the payload registered under {@code cls}; unchanged when absent. */
	@SuppressWarnings("unchecked")
	public <T extends Packaged> Package updateStore(Class<T> cls, UnaryOperator<T> f) {
		return stores.get(cls)
				.map(s -> (Packaged) f.apply((T) s))
				.map(s -> Package.of(substitutions, stores.put(cls, s)))
				.getOrElse(this);
	}
}
