package com.tgac.logic.unification;

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

	LinkedHashMap<Class<? extends Store>, Store> constraints;

	public static Package empty() {
		return Package.of(Substitutions.empty(), LinkedHashMap.empty());
	}

	public static Package of(HashMap<LVar<?>, Term<?>> substitutions,
			LinkedHashMap<Class<? extends Store>, Store> constraints) {
		return Package.of(Substitutions.of(substitutions), constraints);
	}

	public Package withSubstitutions(Substitutions s) {
		return Package.of(s, constraints);
	}

	public Package withSubstitutions(HashMap<LVar<?>, Term<?>> s) {
		return Package.of(Substitutions.of(s), constraints);
	}

	<T> Package put(LVar<T> key, Term<T> value) {
		return Package.of(substitutions.extend(key, value), constraints);
	}

	@SuppressWarnings("unchecked")
	<T> Term<T> get(LVar<T> v) {
		return (Term<T>) substitutions.binding(v);
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

	public Package withStore(Store empty) {
		if (constraints.get(empty.getClass()).isDefined()) {
			return this;
		} else {
			return Package.of(substitutions, constraints.put(empty.getClass(), empty));
		}
	}

	public Package putStore(Store store) {
		return Package.of(substitutions, constraints.put(store.getClass(), store));
	}

	public Package withoutStore(Class<? extends Store> cls) {
		return Package.of(substitutions, constraints.remove(cls));
	}

	/** The store registered under {@code cls}; throws when absent. */
	@SuppressWarnings("unchecked")
	public <T extends Store> T getStore(Class<T> cls) {
		return (T) constraints.get(cls)
				.getOrElseThrow(() -> new IllegalStateException(
						"No store associated with package"));
	}

	/** Prepends {@code c} into its store; unchanged when the store is absent. */
	public Package withStored(Stored c) {
		return constraints.get(c.getStoreClass())
				.map(cs -> cs.prepend(c))
				.map(cs -> Package.of(substitutions, constraints.put(c.getStoreClass(), cs)))
				.getOrElse(this);
	}

	/** Removes {@code c} from its store; unchanged when the store is absent. */
	public Package withoutStored(Stored c) {
		return constraints.get(c.getStoreClass())
				.map(cs -> cs.remove(c))
				.map(cs -> Package.of(substitutions, constraints.put(c.getStoreClass(), cs)))
				.getOrElse(this);
	}

	/** Applies {@code f} to the store registered under {@code cls}; unchanged when absent. */
	@SuppressWarnings("unchecked")
	public <T extends Store> Package updateStore(Class<T> cls, UnaryOperator<T> f) {
		return constraints.get(cls)
				.map(s -> (Store) f.apply((T) s))
				.map(s -> Package.of(substitutions, constraints.put(cls, s)))
				.getOrElse(this);
	}
}
