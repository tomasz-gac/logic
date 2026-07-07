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
import com.tgac.logic.unification.Term;
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
	public static Boolean isAssociated(Package p, Term<?> v) {
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

	/**
	 * Re-runs every constraint store against the newly added substitutions.
	 *
	 * <p>Each store is handed the original package {@code p} so it computes its
	 * prefix (and verifies) against the pre-unification substitutions, not against
	 * a package a previous store already mutated. That keeps composed stores from
	 * starving one another of the prefix.
	 *
	 * <p><b>Limitation — this is not a general constraint solver.</b> The stores
	 * are composed as a single ordered pass ({@code Goal::and}) and each replaces
	 * the substitution map with the new one. This is sound only for independent
	 * domains that do not add substitutions during propagation. It is <em>not</em>
	 * correct in general when:
	 * <ul>
	 *   <li>a store binds a variable during propagation (e.g. a finite domain
	 *       narrowing to a singleton) — a later store's substitution replace can
	 *       clobber that binding; and</li>
	 *   <li>two domains mutually trigger each other — the single pass does not run
	 *       propagation to a fixpoint, so a binding one domain infers may never be
	 *       fed back to the other.</li>
	 * </ul>
	 * Combining domains whose propagation feeds each other is therefore not
	 * guaranteed correct. A sound general solution is a fixpoint (worklist)
	 * propagation loop over a single monotonic substitution.
	 */
	public static Goal processPrefix(HashMap<LVar<?>, Term<?>> newSubstitutions) {
		return p -> {
			// the chokepoint applies the extension exactly once; stores only react
			Package extended = p.extendS(newSubstitutions);
			return p.getConstraints().values().toJavaStream()
					.filter(ConstraintStore.class::isInstance)
					.map(ConstraintStore.class::cast)
					.map(cs -> cs.processPrefix(newSubstitutions, p))
					.reduce(Goal::and)
					.orElseGet(Goal::success)
					.apply(extended);
		};
	}

	public static <T> Goal enforceConstraints(Package p, Term<T> x) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforceConstraints(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	public static <A> Term<A> reify(Package p, Term<A> unifiable, Package renameSubstitutions) {
		return p.getConstraints().values()
				.toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.reduce(Try.success(unifiable),
						(l, cs) -> l.flatMap(u -> Try.of(() -> cs.reify(u, renameSubstitutions, p))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.get();
	}
}
