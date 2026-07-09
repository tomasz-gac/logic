package com.tgac.logic.constraints;

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.logic.constraints.Propagation.resolve;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraints {

	public static <T> Goal unify(Unifiable<T> u, Unifiable<T> v) {
		Goal goal = s -> Cont.defer(() -> MiniKanren.unifyPrefix(s.substitution(), u, v)
				.map(prefix -> resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(nothing())));
		return goal.named(pkg -> pkg.format(u) + " ≣ " + pkg.format(v));
	}

	public static <T> Goal unifyNc(Unifiable<T> u, Unifiable<T> v) {
		Goal goal = s -> Cont.defer(() -> MiniKanren.unifyPrefixUnsafe(s.substitution(), u, v)
				.map(prefix -> resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(nothing())));
		return goal.named(pkg -> pkg.format(u) + " ≣_nc " + pkg.format(v));
	}

	public static <T> Goal unify(Unifiable<T> u, T v) {
		return unify(u, LVal.lval(v));
	}

	public static <T> Goal unifyNc(Unifiable<T> u, T v) {
		return unifyNc(u, LVal.lval(v));
	}

	public static <T> Cont<Reified<T>, Nothing> reify(Package s, Term<T> x) {
		// after renaming every node is an LVal, a ReifiedVar, or a Constrained wrapper
		return enforce(s, x).apply(s)
				.flatMap(Constraints::verifyNoPendingSuspensions)
				.flatMap(s1 -> Cont.defer(() ->
						walkAndRename(x, s1)
								.flatMap(vr -> vr.apply((v, r) ->
										r.isEmpty() ?
												Fiber.done(v) :
												MiniKanren.walkAll(r, v)
														.map(result ->
																s1.getStores() == null ?
																		result :
																		reifyConstraints(s1, result, vr._2))))
								.map(t -> (Reified<T>) t)
								.map(Cont::just)));
	}

	/** Answers may not leave while suspensions pend. */
	private static Cont<Package, Nothing> verifyNoPendingSuspensions(Package s) {
		if (Propagation.suspensionsPending(s)) {
			throw new RuntimeException("Unbound variables during projection");
		}
		return Cont.just(s);
	}

	public static <T> Fiber<Tuple2<Term<T>, Substitutions>> walkAndRename(Term<T> x, Package s1) {
		return MiniKanren.walkAll(s1.substitution(), x)
				.flatMap(v -> MiniKanren.reifyS(Substitutions.empty(), v)
						.map(r -> Tuple.of(v, r)));
	}

	/** Every store commits its constraints before {@code x} is reified. */
	private static <T> Goal enforce(Package p, Term<T> x) {
		return p.getStores().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforce(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	/** Every store renders its residual constraints into the reified answer. */
	private static <A> Term<A> reifyConstraints(Package p, Term<A> unifiable, Substitutions renameSubstitutions) {
		return p.getStores().values()
				.toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.reduce(Try.success(unifiable),
						(l, cs) -> l.flatMap(u -> Try.of(() -> cs.reify(u, renameSubstitutions, p))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.get();
	}

}
