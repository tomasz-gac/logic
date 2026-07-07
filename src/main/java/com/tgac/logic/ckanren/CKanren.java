package com.tgac.logic.ckanren;

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.logic.ckanren.Propagation.resolve;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.ckanren.store.ConstraintStore;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CKanren {

	public static <T> Goal unify(Unifiable<T> u, Unifiable<T> v) {
		Goal goal = s -> Cont.defer(() -> MiniKanren.unifyPrefix(s, u, v)
				.map(prefix -> resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(nothing())));
		return goal.named(pkg -> MiniKanren.format(pkg, u) + " ≣ " + MiniKanren.format(pkg, v));
	}

	public static <T> Goal unifyNc(Unifiable<T> u, Unifiable<T> v) {
		Goal goal = s -> Cont.defer(() -> MiniKanren.unifyPrefixUnsafe(s, u, v)
				.map(prefix -> resolve(prefix).apply(s))
				.getOrElse(() -> Cont.complete(nothing())));
		return goal.named(pkg -> MiniKanren.format(pkg, u) + " ≣_nc " + MiniKanren.format(pkg, v));
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
				.flatMap(s1 -> Cont.defer(() ->
						calculateSubstitutionAndRenamePackage(x, s1)
								.flatMap(vr -> vr.apply((v, r) ->
										r.getSubstitutions().isEmpty() ?
												Fiber.done(v) :
												MiniKanren.walkAll(r, v)
														.map(result ->
																s1.getConstraints() == null ?
																		result :
																		reifyConstraints(s1, result, vr._2))))
								.map(t -> (Reified<T>) t)
								.map(Cont::just)));
	}

	public static <T> Fiber<Tuple2<Term<T>, Package>> calculateSubstitutionAndRenamePackage(Term<T> x, Package s1) {
		return MiniKanren.walkAll(s1, x)
				.flatMap(v -> MiniKanren.reifyS(Package.empty(), v)
						.map(r -> Tuple.of(v, r)));
	}

	/** Every store commits its constraints before {@code x} is reified. */
	private static <T> Goal enforce(Package p, Term<T> x) {
		return p.getConstraints().values().toJavaStream()
				.filter(ConstraintStore.class::isInstance)
				.map(ConstraintStore.class::cast)
				.map(cs -> cs.enforce(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	/** Every store renders its residual constraints into the reified answer. */
	private static <A> Term<A> reifyConstraints(Package p, Term<A> unifiable, Package renameSubstitutions) {
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
