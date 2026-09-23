package org.clauseway.logic.constraints;

import org.clauseway.functional.Exceptions;
import org.clauseway.functional.category.Nothing;
import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.constraints.store.Constraint;
import org.clauseway.logic.constraints.store.Factor;
import org.clauseway.logic.constraints.store.Renaming;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.LVal;
import org.clauseway.logic.unification.MiniKanren;
import org.clauseway.logic.unification.Reified;
import org.clauseway.logic.unification.Substitutions;
import org.clauseway.logic.unification.Term;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constraints {

	public static <T> Posting unify(Unifiable<T> u, Unifiable<T> v) {
		return Unification.of(u, v, false)
				.named(pkg -> pkg.format(u) + " ≡ " + pkg.format(v));
	}

	public static <T> Posting unifyNc(Unifiable<T> u, Unifiable<T> v) {
		return Unification.of(u, v, true)
				.named(pkg -> pkg.format(u) + " ≡_nc " + pkg.format(v));
	}

	public static <T> Posting unify(Unifiable<T> u, T v) {
		return unify(u, LVal.lval(v));
	}

	public static <T> Posting unifyNc(Unifiable<T> u, T v) {
		return unifyNc(u, LVal.lval(v));
	}

	public static <T> Cont<Reified<T>, Nothing> reify(Package s, Term<T> x) {
		// after renaming every node is an LVal, a Any, or a Constrained wrapper
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
																		reifyConstraints(s1, result, Renaming.of(vr._2)))))
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
				.filter(Constraint.class::isInstance)
				.map(entry -> (Factor<?>) ((Constraint<?>) entry).getFactor())
				.map(cs -> cs.enforce(x))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	/** Every store renders its residual constraints into the reified answer. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <A> Term<A> reifyConstraints(Package p, Term<A> unifiable, Renaming renaming) {
		return p.getStores().values()
				.toJavaStream()
				.filter(Constraint.class::isInstance)
				.map(entry -> (Constraint<?>) entry)
				.reduce(Try.success(unifiable),
						(l, cs) -> l.flatMap(u -> Try.of(() ->
								(Term<A>) ((Factor) cs.getFactor()).reify((Theory) cs.getTheory(), u, renaming, p))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.get();
	}

}
