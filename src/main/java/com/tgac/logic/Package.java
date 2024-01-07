package com.tgac.logic;
import com.sun.org.slf4j.internal.LoggerFactory;
import com.tgac.functional.Exceptions;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.ConstraintStore;
import com.tgac.logic.cKanren.PackageAccessor;
import io.vavr.Predicates;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.function.UnaryOperator;

import static com.tgac.functional.Exceptions.format;
import static com.tgac.logic.MiniKanren.walk;
@Value
@RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "of")
public class Package {
	private static HashMap<Class<? extends ConstraintStore>, ConstraintStore> EMPTY_STORES = HashMap.empty();

	public static synchronized <T extends ConstraintStore> void register(T emptyStore) {
		EMPTY_STORES = EMPTY_STORES.put(emptyStore.getClass(), emptyStore);
		LoggerFactory.getLogger(Package.class).debug(emptyStore + " registered");
	}

	// substitutions
	HashMap<LVar<?>, Unifiable<?>> substitutions;
	// separateness constraints
	List<HashMap<LVar<?>, Unifiable<?>>> sConstraints;

	HashMap<Class<? extends ConstraintStore>, ConstraintStore> constraints;

	public static Package empty() {
		// due to static initialization of store registry, this has to be called at runtime
		return new Package(HashMap.empty(), List.empty(), EMPTY_STORES);
	}

	public Package extendS(HashMap<LVar<?>, Unifiable<?>> s) {
		return new Package(substitutions.merge(s), sConstraints, constraints);
	}

	<T> Package put(LVar<T> key, Unifiable<T> value) {
		return Package.of(substitutions.put(key, value), sConstraints, constraints);
	}

	@SuppressWarnings("unchecked")
	<T> Option<Unifiable<T>> get(LVar<T> v) {
		return substitutions.get(v).map(w -> (Unifiable<T>) w);
	}

	@SuppressWarnings("unchecked")
	public <T extends ConstraintStore> T get(Class<T> cls) {
		return constraints.get(cls)
				.map(cs -> (T) cs)
				.getOrElseThrow(format(IllegalStateException::new, "No store associated with class %s", cls));
	}

	public PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> prefix) {
		return constraints.toJavaStream()
				.map(Tuple2::_2)
				.map(cs -> cs.processPrefix(prefix))
				.reduce(PackageAccessor::compose)
				.orElseGet(PackageAccessor::identity);
	}

	Package putSepConstraint(HashMap<LVar<?>, Unifiable<?>> constraint) {
		return Package.of(substitutions, sConstraints.prepend(constraint), constraints);
	}

	public Package withoutConstraint(Constraint c) {
		return Package.of(substitutions, sConstraints, updateConstraints(c.getTag(), cs -> cs.remove(c)));
	}

	/**
	 * Checks whether any item within v is unbound within r Original name: anyVar
	 */
	public Boolean isAssociated(Unifiable<?> v) {
		return v.asVar()
				.map(lvar -> walk(this, lvar) != lvar)
				.getOrElse(true);
	}

	public Package withConstraint(Constraint c) {
		boolean atLeaseOneIsBound = c.getArgs().toJavaStream()
				.anyMatch(Predicates.not(this::isAssociated));

		return atLeaseOneIsBound ?
				Package.of(substitutions, sConstraints, updateConstraints(c.getTag(), cs -> cs.prepend(c))) :
				this;
	}

	public Package withSubstitutionsFrom(Package aPackage) {
		return Package.of(aPackage.getSubstitutions(), sConstraints, constraints);
	}

	public long size() {
		return substitutions.size();
	}

	public Package withoutConstraints() {
		return Package.of(substitutions, List.empty(), constraints);
	}

	public <T extends ConstraintStore> Package updateC(Class<T> cls, UnaryOperator<T> f) {
		return Package.of(
				substitutions,
				sConstraints,
				updateConstraints(cls, cs ->
						Types.<T> castAs(cs, ConstraintStore.class)
								.map(f).get()));
	}

	private HashMap<Class<? extends ConstraintStore>, ConstraintStore> updateConstraints(Class<? extends ConstraintStore> cls, UnaryOperator<ConstraintStore> f) {
		return constraints.put(
				cls,
				f.apply(constraints.get(cls)
						.getOrElseThrow(format(IllegalStateException::new, "No store associated with class %s", cls))));
	}

	public <T> Goal enforceConstraints(Unifiable<T> x) {
		return constraints.toJavaStream()
				.map(Tuple2::_2)
				.map(cs -> cs.enforceConstraints(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}
	public <A> Try<Unifiable<A>> reify(Unifiable<A> unifiable, Package renameSubstitutions) {
		return constraints.toJavaStream()
				.map(Tuple2::_2)
				.reduce(Try.success(unifiable),
						(acc, cs) -> acc.flatMap(r -> cs.reify(r, renameSubstitutions, this)),
						Exceptions.throwingBiOp(UnsupportedClassVersionError::new));
	}
}
