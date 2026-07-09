package com.tgac.logic.separate;

import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.logic.ckanren.CKanren.unify;
import static com.tgac.logic.unification.MiniKanren.applyOnBoth;
import static com.tgac.logic.unification.MiniKanren.format;
import static com.tgac.logic.unification.MiniKanren.walkAll;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.goals.Matche;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.stream.Stream;

public class Disequality {

	public static <T> Goal separate(Unifiable<T> lhs, Unifiable<T> rhs) {
		return a -> {
			Package s = NeqConstraints.register(a);
			// trial unification: the prefix IS the disequality's meaning — the exact
			// simultaneous bindings that must never all hold
			return Cont.defer(() -> MiniKanren.unifyPrefix(s.substitution(), lhs, rhs)
					.map(prefix -> prefix.isEmpty() ?
							// they already unify: the disequality is violated
							Cont.<Package, Nothing> complete(Nothing.nothing()) :
							Cont.<Package, Nothing> just(s.withStored(NeqConstraint.of(prefix.toMap()))))
					// they cannot unify: already separate, nothing to record
					.getOrElse(() -> Cont.just(s)));
		};
	}

	public static <T> Goal separate(Unifiable<T> lhs, T rhs) {
		return separate(lhs, LVal.lval(rhs));
	}

	public static <T> Goal rembero(Unifiable<LList<T>> ls, Unifiable<T> x, Unifiable<LList<T>> out) {
		return Matche.matche(ls, Matche.llist(() -> unify(out, LList.empty())))
				.or(Matche.matche(ls, Matche.llist((a, d) ->
						unify(x, a)
								.and(unify(out, d)))))
				.or(Matche.matche(ls, Matche.llist((a, d) -> Logic.<LList<T>> exist(res ->
						separate(a, x)
								.and(unify(out, LList.of(a, res)))
								.and(Goal.defer(() -> rembero(d, x, res)))))))
				.named(pkg -> pkg.format(x) + " ⊄ " + Logic.formatLList(pkg, ls) + " ≣ " + pkg.format(out));
	}

	public static <A> Goal distincto(Unifiable<LList<A>> distinct) {
		return Matche.matche(distinct,
						Matche.llist(() -> Goal.success()),
						Matche.llist(a -> Goal.success()),
						Matche.llist((a, b, d) -> separate(a, b)
								.and(Goal.defer(() -> distincto(LList.of(a, d))))
								.and(Goal.defer(() -> distincto(LList.of(b, d))))))
				.named(pkg -> "distincto(" + Logic.formatLList(pkg, distinct) + ")");
	}

	/**
	 * Re-verifies every record against the given substitutions: none = some record
	 * is violated (all its pairs hold simultaneously); otherwise the surviving
	 * records, simplified to their remaining prefixes.
	 */
	static Option<List<NeqConstraint>> verifyAndSimplify(
			List<NeqConstraint> constraints,
			Substitutions substitutions) {
		return verifyAndSimplifyConstraints(constraints, List.empty(), substitutions);
	}

	private static Option<List<NeqConstraint>> verifyAndSimplifyConstraints(
			List<NeqConstraint> constraints,
			List<NeqConstraint> newConstraints,
			Substitutions s) {
		return constraints.toJavaStream()
				.reduce(Option.of(newConstraints),
						(acc, c) -> acc.flatMap(currentConstraints ->
								verificationStep(s, currentConstraints, c)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	private static Option<List<NeqConstraint>> verificationStep(
			Substitutions substitutions,
			List<NeqConstraint> newConstraints,
			NeqConstraint constraint) {
		Option<HashMap<LVar<?>, Term<?>>> delta = unifyConstraints(constraint, substitutions);

		if (delta.isDefined()) {
			return delta
					// an empty delta means the pairs all hold already — all
					// simultaneous separateness constraints are violated,
					// so we fail the substitution
					.filter(d -> !d.isEmpty())
					// a non-empty delta is exactly the bindings still needed
					// for the record to be violated — the simplified record.
					// This way, we simplify constraint store on the fly
					.map(d -> newConstraints.append(NeqConstraint.of(d)));
		} else {
			// if unification fails, then constraint is redundant
			// because substitutions already contain bound values
			// that are consistent with it
			return Option.of(newConstraints);
		}
	}

	/**
	 * Checks whether all of the constraint's pairs unify within s simultaneously.
	 *
	 * @param simultaneousConstraints List of constraints that must be simultaneously true
	 * @param s current substitution map
	 * @return the collected delta of bindings the pairs still need to all hold —
	 * 		empty means they hold already; none means some pair cannot
	 */
	private static Option<HashMap<LVar<?>, Term<?>>> unifyConstraints(
			NeqConstraint simultaneousConstraints,
			Substitutions s) {
		return simultaneousConstraints.getSeparate().toJavaStream()
				.map(t -> t.map(applyOnBoth(u -> (Term<Object>) u)))
				.reduce(Option.of(Tuple.of(s, HashMap.<LVar<?>, Term<?>> empty())),
						(acc, lr) -> acc.flatMap(sd ->
								// unification over bare substitutions cannot recurse into
								// constraint processing — it only collects the prefix
								MiniKanren.unifyPrefix(sd._1, lr._1, lr._2)
										.get()
										.map(prefix -> Tuple.of(
												prefix.appliedTo(sd._1),
												prefix.appliedTo(sd._2)))),
						Exceptions.throwingBiOp(UnsupportedOperationException::new))
				.map(Tuple2::_2);
	}

	static Fiber<List<NeqConstraint>> walkAllConstraints(
			List<NeqConstraint> constraints,
			Substitutions s) {
		return constraints.toJavaStream()
				.map(c -> walkAllConstraint(s, c.getSeparate())
						.map(Stream::of))
				.reduce((l, r) -> Fiber.zip(l, r)
						.map(lr -> lr.apply(Stream::concat)))
				.orElseGet(() -> done(Stream.empty()))
				.map(stream -> stream
						.map(NeqConstraint::of)
						.collect(List.collector()));
	}

	private static Fiber<HashMap<LVar<?>, Term<?>>> walkAllConstraint(Substitutions s, HashMap<LVar<?>, Term<?>> c) {
		return c.toJavaStream()
				.map(valSub -> valSub.map(
								val -> walkAll(s, val)
										// this should be right since lhs of a substitution is unbound and unique
										.map(u -> u.asVar().get()),
								sub -> walkAll(s, sub))
						.apply(Fiber::zip))
				.reduce(done(HashMap.empty()),
						(acc, v) -> Fiber.zip(acc, v)
								.map(ms -> ms.apply(HashMap::put)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static Fiber<List<HashMap<Term<?>, Term<?>>>> renameForDisplay(
			List<NeqConstraint> constraints,
			Substitutions renameSubstitutions) {
		return constraints.toJavaStream()
				.map(c -> renameConstraint(renameSubstitutions, c.getSeparate())
						.map(Stream::of))
				.reduce((l, r) -> Fiber.zip(l, r)
						.map(lr -> lr.apply(Stream::concat)))
				.orElseGet(() -> done(Stream.empty()))
				.map(stream -> stream.collect(List.collector()));
	}

	private static Fiber<HashMap<Term<?>, Term<?>>> renameConstraint(Substitutions r, HashMap<LVar<?>, Term<?>> c) {
		return c.toJavaStream()
				.map(pair -> Fiber.zip(
						walkAll(r, pair._1.getObjectTerm()),
						walkAll(r, pair._2.getObjectTerm())))
				.reduce(done(HashMap.<Term<?>, Term<?>> empty()),
						(acc, v) -> Fiber.zip(acc, v)
								.map(ms -> ms._1.put(ms._2._1, ms._2._2)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static List<NeqConstraint> purify(List<NeqConstraint> c, Substitutions r) {
		return c.toJavaStream()
				.map(cc -> purifySingle(cc, r))
				.map(Stream::of)
				.reduce(Stream::concat)
				.orElseGet(Stream::empty)
				.filter(cs -> !cs.getSeparate().isEmpty())
				.collect(List.collector());
	}

	/** Whether the term denotes something in {@code p}: a value, or a bound variable. */
	private static boolean isBound(Substitutions p, Term<?> v) {
		return v.asVar()
				.map(lvar -> p.walk(lvar) != lvar)
				.getOrElse(true);
	}

	private static NeqConstraint purifySingle(
			NeqConstraint constraints,
			Substitutions r) {
		return NeqConstraint.of(
				constraints.getSeparate().toJavaStream()
						.filter(c -> isBound(r, c._1) && isBound(r, c._2))
						.reduce(HashMap.empty(), (c, head) -> head.apply(c::put),
								Exceptions.throwingBiOp(UnsupportedOperationException::new)));
	}

	private static Boolean isConstraintSubsumed(
			NeqConstraint constraints,
			List<NeqConstraint> accConstraints) {
		return accConstraints.toJavaStream()
				.reduce(false,
						(r, v) -> r || unifyConstraints(v, Substitutions.of(constraints.getSeparate()))
								.filter(HashMap::isEmpty)
								.map(__ -> true)
								.getOrElse(() -> false),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static Fiber<List<NeqConstraint>> removeSubsumed(
			List<NeqConstraint> constraints,
			List<NeqConstraint> constraintAcc) {
		if (constraints.isEmpty()) {
			return done(constraintAcc);
		} else {
			return Fiber.zip(
							// constraint subsumes another existing constraint
							done(isConstraintSubsumed(constraints.head(), constraintAcc)),
							// constraint subsumed by previously processed
							done(isConstraintSubsumed(constraints.head(), constraints.tail())))
					.map(lr -> lr.apply(Boolean::logicalOr))
					.flatMap(isSubsumed -> isSubsumed ?
							// skip this constraint
							removeSubsumed(constraints.tail(), constraintAcc) :
							// add to result and process next
							removeSubsumed(
									constraints.tail(),
									constraintAcc.prepend(constraints.head())));
		}
	}
}
