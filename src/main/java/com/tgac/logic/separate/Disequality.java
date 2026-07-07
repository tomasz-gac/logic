package com.tgac.logic.separate;

import static com.tgac.functional.fibers.Fiber.done;
import static com.tgac.logic.ckanren.CKanren.unify;
import static com.tgac.logic.ckanren.StoreSupport.isAssociated;
import static com.tgac.logic.ckanren.StoreSupport.withConstraint;
import static com.tgac.logic.ckanren.StoreSupport.withoutConstraints;
import static com.tgac.logic.unification.MiniKanren.applyOnBoth;
import static com.tgac.logic.unification.MiniKanren.format;
import static com.tgac.logic.unification.MiniKanren.prefixS;
import static com.tgac.logic.unification.MiniKanren.unify;
import static com.tgac.logic.unification.MiniKanren.walkAll;

import com.tgac.functional.Exceptions;
import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.goals.Matche;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class Disequality {

	public static <T> Goal separate(Unifiable<T> lhs, Unifiable<T> rhs) {
		return a -> {
			Package s = NeqConstraints.register(a);
			return Cont.defer(() ->
					unify(withoutConstraints(s), lhs, rhs)
							.getFiber()
							.map(unificationResult -> {
								switch (verifySeparate(unificationResult, s)) {
									case UNIFIED:
										return Cont.complete(Nothing.nothing());
									case ALREADY_SEPARATE:
										return Cont.just(s);
									case SEPARATE_FOR_NOW:
										HashMap<LVar<?>, Term<?>> prefix = prefixS(
												s.getSubstitutions(),
												unificationResult.get().getSubstitutions());
										return bridgeToFiniteDomain(s, prefix)
												.map(exclude -> exclude.apply(s))
												.getOrElse(() -> Cont.just(withConstraint(s,
														NeqConstraint.of(prefix))));
									default:
										throw new UnsupportedOperationException();
								}
							}));
		};
	}

	/**
	 * The Neq half of the disequality bridge: a single-pair prefix against a ground
	 * value, on a variable with a finite domain, becomes a domain exclusion instead
	 * of a stored record (cKanren's FD/≠ integration). Applies only at record
	 * creation — a disequality stated before the domain exists keeps its record,
	 * which stays correct through verification.
	 */
	private static Option<Goal> bridgeToFiniteDomain(Package s, HashMap<LVar<?>, Term<?>> prefix) {
		return Option.of(prefix)
				.filter(pf -> pf.size() == 1)
				.map(pf -> pf.iterator().next())
				.filter(binding -> binding._2.isVal())
				.flatMap(binding -> FiniteDomain.excludeFromDomain(s, binding._1, binding._2.get()));
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
				.named(pkg -> format(pkg, x) + " ⊄ " + Logic.formatLList(pkg, ls) + " ≣ " + format(pkg, out));
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

	private enum VerificationResult {
		ALREADY_SEPARATE,
		UNIFIED,
		SEPARATE_FOR_NOW
	}

	private static VerificationResult verifySeparate(
			Option<Package> sepSubstitutions,
			Package origSubst) {
		if (sepSubstitutions.isEmpty()) {
			// unification failed, so lhs and rhs are already separate
			return VerificationResult.ALREADY_SEPARATE;
		} else {
			Package result = sepSubstitutions.get();
			if (result.getSubstitutions() == origSubst.getSubstitutions()) {
				// unification succeeded without extending S,
				// so it is already satisfied and we fail
				return VerificationResult.UNIFIED;
			} else {
				// New substitutions added, so there are some
				// constraints to check on every unification
				return VerificationResult.SEPARATE_FOR_NOW;
			}
		}
	}

	static Option<Package> verifyUnify(Package newPackage, Package a) {
		// substitutions haven't changed, so constraints are not violated
		if (newPackage.getSubstitutions() == a.getSubstitutions()) {
			return Option.of(a);
		} else {
			return verifyAndSimplifyConstraints(
					NeqConstraints.get(a)
							.getConstraints(),
					List.empty(),
					newPackage.getSubstitutions())
					// Rebuilds the package from newPackage's substitutions and stores,
					// replacing only the disequality store with its simplified form. This
					// is correct when disequality is the only constraint domain in play.
					// Under multiple domains it is not sound in general: newPackage holds
					// the substitutions this store was handed, so a binding a different
					// store added earlier in the same processPrefix pass is not reflected
					// here. See StoreSupport#processPrefix for the composition limitation.
					.map(c -> Package.of(
							newPackage.getSubstitutions(),
							newPackage.getConstraints().put(NeqConstraints.class, NeqConstraints.of(c))));
		}
	}

	private static Option<List<NeqConstraint>> verifyAndSimplifyConstraints(
			List<NeqConstraint> constraints,
			List<NeqConstraint> newConstraints,
			HashMap<LVar<?>, Term<?>> s) {
		return constraints.toJavaStream()
				.reduce(Option.of(newConstraints),
						(acc, c) -> acc.flatMap(currentConstraints ->
								verificationStep(s, currentConstraints, c)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	private static Option<List<NeqConstraint>> verificationStep(
			HashMap<LVar<?>, Term<?>> substitutions,
			List<NeqConstraint> newConstraints,
			NeqConstraint constraint) {
		Option<HashMap<LVar<?>, Term<?>>> unification = unifyConstraints(constraint, substitutions);

		if (unification.isDefined()) {
			return unification
					// if unification succeeds without extending substitutions
					// then all simultaneous separateness constraints
					// are violated, so we fail the substitution
					.filter(s1 -> substitutions != s1)
					// if unification succeeds by extending substitutions,
					// then some simultaneous constraints are not violated,
					// and we append these constraints to list.
					// This way, we simplify constraint store on the fly
					.map(s1 -> newConstraints.append(NeqConstraint.of(prefixS(substitutions, s1))));
		} else {
			// if unification fails, then constraint is redundant
			// because substitutions already contain bound values
			// that are consistent with it
			return Option.of(newConstraints);
		}
	}

	/**
	 * Checks whether all constraints unify within s simultaneously.
	 *
	 * @param simultaneousConstraints List of constraints that must be simultaneously true
	 * @param s current substitution map
	 * @return s after unification
	 */
	private static Option<HashMap<LVar<?>, Term<?>>> unifyConstraints(
			NeqConstraint simultaneousConstraints,
			HashMap<LVar<?>, Term<?>> s) {
		return simultaneousConstraints.getSeparate().toJavaStream()
				.map(t -> t.map(applyOnBoth(u -> (Term<Object>) u)))
				.reduce(Option.of(s),
						(acc, lr) -> acc.flatMap(s1 ->
								// This cannot recurse deeply via verifyUnify
								// because there are no constraints in the package
								unify(Package.empty().withSubstitutions(s1),
										lr._1, lr._2)
										.get()
										.map(Package::getSubstitutions)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static Fiber<List<NeqConstraint>> walkAllConstraints(
			List<NeqConstraint> constraints,
			Package s) {
		return constraints.toJavaStream()
				.map(c -> walkAllConstraint(s, c.getSeparate())
						.map(java.util.stream.Stream::of))
				.reduce((l, r) -> Fiber.zip(l, r)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.orElseGet(() -> done(java.util.stream.Stream.empty()))
				.map(stream -> stream
						.map(NeqConstraint::of)
						.collect(List.collector()));
	}

	private static Fiber<HashMap<LVar<?>, Term<?>>> walkAllConstraint(Package s, HashMap<LVar<?>, Term<?>> c) {
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
			Package renamePackage) {
		return constraints.toJavaStream()
				.map(c -> renameConstraint(renamePackage, c.getSeparate())
						.map(java.util.stream.Stream::of))
				.reduce((l, r) -> Fiber.zip(l, r)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.orElseGet(() -> done(java.util.stream.Stream.empty()))
				.map(stream -> stream.collect(List.collector()));
	}

	private static Fiber<HashMap<Term<?>, Term<?>>> renameConstraint(Package r, HashMap<LVar<?>, Term<?>> c) {
		return c.toJavaStream()
				.map(pair -> Fiber.zip(
						walkAll(r, pair._1.getObjectTerm()),
						walkAll(r, pair._2.getObjectTerm())))
				.reduce(done(HashMap.<Term<?>, Term<?>> empty()),
						(acc, v) -> Fiber.zip(acc, v)
								.map(ms -> ms._1.put(ms._2._1, ms._2._2)),
						Exceptions.throwingBiOp(UnsupportedOperationException::new));
	}

	static List<NeqConstraint> purify(List<NeqConstraint> c, Package r) {
		return c.toJavaStream()
				.map(cc -> purifySingle(cc, r))
				.map(java.util.stream.Stream::of)
				.reduce(java.util.stream.Stream::concat)
				.orElseGet(java.util.stream.Stream::empty)
				.filter(cs -> !cs.getSeparate().isEmpty())
				.collect(List.collector());
	}

	private static NeqConstraint purifySingle(
			NeqConstraint constraints,
			Package r) {
		return NeqConstraint.of(
				constraints.getSeparate().toJavaStream()
						.filter(c -> isAssociated(r, c._1) && isAssociated(r, c._2))
						.reduce(HashMap.empty(), (c, head) -> head.apply(c::put),
								Exceptions.throwingBiOp(UnsupportedOperationException::new)));
	}

	private static Boolean isConstraintSubsumed(
			NeqConstraint constraints,
			List<NeqConstraint> accConstraints) {
		return accConstraints.toJavaStream()
				.reduce(false,
						(r, v) -> r || unifyConstraints(v, constraints.getSeparate())
								.filter(c -> c == constraints.getSeparate())
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
