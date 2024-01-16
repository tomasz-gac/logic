package com.tgac.logic.ckanren;

import com.tgac.functional.Exceptions;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.UnaryOperator;
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreSupport {

	public static ConstraintStore getConstraintStore(Package p) {
		return Option.of(p.getConstraints())
				.map(ConstraintStore.class::cast)
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "No store associated with package"));
	}

	public static Package withoutConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				Option.of(p.getConstraints())
						.map(cs -> cs.remove(c))
						.getOrElse(() -> null));
	}

	/**
	 * Checks whether any item within v is unbound within r Original name: anyVar
	 */
	public static Boolean isAssociated(Package p, Unifiable<?> v) {
		return v.asVar()
				.map(lvar -> MiniKanren.walk(p, lvar) != lvar)
				.getOrElse(true);
	}

	public static Package withConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				Option.of(p.getConstraints())
						.map(cs -> cs.prepend(c))
						.getOrElse(() -> null));
	}

	public static Package withoutConstraints(Package p) {
		return Package.of(p.getSubstitutions(), null);
	}

	public static <T extends ConstraintStore> Package updateC(Package p, UnaryOperator<T> f) {
		return Package.of(
				p.getSubstitutions(),
				Option.of(p.getConstraints())
						.flatMap(Types.<T> castAs(ConstraintStore.class))
						.map(f)
						.getOrElse(() -> null));
	}

	public static Option<Package> processPrefix(Package p, HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return Option.of(p.getConstraints())
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.processPrefix(newSubstitutions))
				.getOrElse(s -> Option.of(p.withSubstitutions(newSubstitutions)))
				.apply(p);
	}

	public static <T> Goal enforceConstraints(Package p, Unifiable<T> x) {
		return Option.of(p.getConstraints())
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforceConstraints(x))
				.getOrElse(Goal::success);
	}

	public static <A> Unifiable<A> reify(Package p, Unifiable<A> unifiable, Package renameSubstitutions) {
		return Option.of(p.getConstraints())
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.reify(unifiable, renameSubstitutions, p))
				.getOrElse(() -> unifiable);
	}
}
