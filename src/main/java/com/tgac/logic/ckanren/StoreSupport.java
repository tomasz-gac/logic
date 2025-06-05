package com.tgac.logic.ckanren;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.reflection.Types;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreSupport {

	public static <T extends Store> T getConstraintStore(Package p, Class<T> cls) {
		return p.getConstraints().get(cls)
				.map(Types.<T>cast())
				.getOrElseThrow(Exceptions.format(IllegalStateException::new, "No store associated with package"));
	}

	public static Package withoutConstraint(Package p, Stored c) {
		return Package.of(p.getSubstitutions(),
				p.getConstraints()
						.get(c.getStoreClass())
						.map(cs -> cs.remove(c))
						.map(newStore -> p.getConstraints()
								.put(c.getStoreClass(), newStore))
						.getOrElse(p::getConstraints));
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
				p.getConstraints().get(c.getStoreClass())
						.map(cs -> cs.prepend(c))
						.map(s -> p.getConstraints().put(c.getStoreClass(), s))
						.getOrElse(p::getConstraints));
	}

	public static Package withoutConstraints(Package p) {
		return Package.of(p.getSubstitutions(), null);
	}

	public static <T extends ConstraintStore> Package updateC(Package p, Class<T> cls, UnaryOperator<T> f) {
		return Package.of(
				p.getSubstitutions(),
				p.getConstraints().put(
						cls,
						p.getConstraints().get(cls)
								.flatMap(Types.<T> castAs(ConstraintStore.class))
								.map(f)
								.getOrElse(() -> null)));
	}

	public static Goal processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions) {
		return p -> p.getConstraints().values().toJavaStream()
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.processPrefix(newSubstitutions))
				.reduce(Goal::and)
				.orElseGet(() -> s -> Cont.just(p.withSubstitutions(newSubstitutions)))
				.apply(p);
	}

	public static <T> Goal enforceConstraints(Package p, Unifiable<T> x) {
		return p.getConstraints().values().toJavaStream()
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforceConstraints(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	public static <A> Unifiable<A> reify(Package p, Unifiable<A> unifiable, Package renameSubstitutions) {
		return p.getConstraints().values()
				.toJavaStream()
				.map(ConstraintStore.class::cast)
				.reduce(Try.success(unifiable),
						(l, cs) -> l.flatMap(u -> Try.of(() -> cs.reify(u, renameSubstitutions, p))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.get();
	}
}
