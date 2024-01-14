package com.tgac.logic.unification;
import com.tgac.functional.Exceptions;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.ckanren.parameters.Store;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.UnaryOperator;
@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {

	HashMap<LVar<?>, Unifiable<?>> substitutions;

	Store constraints;

	public static Package empty() {
		return new Package(HashMap.empty(), null);
	}

	public Package extendS(HashMap<LVar<?>, Unifiable<?>> s) {
		return new Package(substitutions.merge(s), constraints);
	}

	public Package withSubstitutions(HashMap<LVar<?>, Unifiable<?>> s) {
		return new Package(s, constraints);
	}

	<T> Package put(LVar<T> key, Unifiable<T> value) {
		return Package.of(substitutions.put(key, value), constraints);
	}

	@SuppressWarnings("unchecked")
	<T> Option<Unifiable<T>> get(LVar<T> v) {
		return substitutions.get(v).map(w -> (Unifiable<T>) w);
	}

	public Store getConstraintStore() {
		return Option.of(constraints)
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "No store associated with package"));
	}

	public Package withoutConstraint(Stored c) {
		return Package.of(substitutions,
				Option.of(constraints)
						.map(cs -> cs.remove(c))
						.getOrElse(() -> null));
	}

	/**
	 * Checks whether any item within v is unbound within r Original name: anyVar
	 */
	public Boolean isAssociated(Unifiable<?> v) {
		return v.asVar()
				.map(lvar -> MiniKanren.walk(this, lvar) != lvar)
				.getOrElse(true);
	}

	public Package withConstraint(Stored c) {
		return Package.of(substitutions,
				Option.of(constraints)
						.map(cs -> cs.prepend(c))
						.getOrElse(() -> null));
	}

	public long size() {
		return substitutions.size();
	}

	public Package withoutConstraints() {
		return Package.of(substitutions, null);
	}

	public <T extends Store> Package updateC(UnaryOperator<T> f) {
		return Package.of(
				substitutions,
				Option.of(constraints)
						.flatMap(Types.<T> castAs(Store.class))
						.map(f)
						.getOrElse(() -> null));
	}

	public Option<Package> processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return Option.of(constraints)
				.map(cs -> cs.processPrefix(newSubstitutions))
				.getOrElse(s -> Option.of(withSubstitutions(newSubstitutions)))
				.apply(this);
	}

	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return Option.of(constraints)
				.map(cs -> cs.enforceConstraints(x))
				.getOrElse(Goal::success);
	}

	public <A> Try<Unifiable<A>> reify(Unifiable<A> unifiable, Package renameSubstitutions) {
		return Option.of(constraints)
				.map(cs -> cs.reify(unifiable, renameSubstitutions, this))
				.getOrElse(() -> Try.success(unifiable));
	}
	public Package withConstraintStore(Store empty) {
		if (constraints != null) {
			if (empty.getClass().isInstance(constraints)) {
				return this;
			} else {
				throw new IllegalStateException("Constraint store already exists: " + constraints);
			}
		} else {
			return Package.of(substitutions, empty);
		}
	}
}
