package com.tgac.logic.separate;
import com.tgac.functional.Exceptions;
import com.tgac.functional.recursion.Recur;
import com.tgac.logic.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;

import static com.tgac.functional.Exceptions.throwingBiOp;
import static com.tgac.functional.recursion.Recur.done;
import static com.tgac.logic.unification.MiniKanren.applyOnBoth;
import static com.tgac.logic.unification.MiniKanren.prefixS;
import static com.tgac.logic.unification.MiniKanren.unify;
import static com.tgac.logic.unification.MiniKanren.walkAll;
public class SeparateGoals {

	public static <T> Goal separate(Unifiable<T> lhs, Unifiable<T> rhs) {
		return s -> {
			Option<Package> unificationResult = unify(s.withoutConstraints(), lhs, rhs);
			switch (verifySeparate(unificationResult, s)) {
				case UNIFIED:
					return Stream.empty();
				case ALREADY_SEPARATE:
					return Stream.of(s);
				case SEPARATE_FOR_NOW:
					return Stream.of(s.withConstraint(
							NeqConstraint.of(
									prefixS(
											s.getSubstitutions(),
											unificationResult.get().getSubstitutions()))));
				default:
					throw new UnsupportedOperationException();
			}
		};
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
					SeparatenessConstraints.get(a)
							.getConstraints(),
					List.empty(),
					newPackage.getSubstitutions())
					// TODO : verify
					.map(c -> Package.of(
							newPackage.getSubstitutions(),
							newPackage.getConstraints()
									.put(SeparatenessConstraints.class, SeparatenessConstraints.of(c))));
		}
	}

	private static Option<List<NeqConstraint>> verifyAndSimplifyConstraints(
			List<NeqConstraint> constraints,
			List<NeqConstraint> newConstraints,
			HashMap<LVar<?>, Unifiable<?>> s) {
		return constraints.toJavaStream()
				.reduce(Option.of(newConstraints),
						(acc, c) -> acc.flatMap(currentConstraints ->
								verificationStep(s, currentConstraints, c)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	private static Option<List<NeqConstraint>> verificationStep(
			HashMap<LVar<?>, Unifiable<?>> substitutions,
			List<NeqConstraint> newConstraints,
			NeqConstraint constraint) {
		Option<HashMap<LVar<?>, Unifiable<?>>> unification = unifyConstraints(constraint, substitutions);

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
	 * @param simultaneousConstraints
	 * 		List of constraints that must be simultaneously true
	 * @param s
	 * 		current substitution map
	 * @return s after unification
	 */
	private static Option<HashMap<LVar<?>, Unifiable<?>>> unifyConstraints(
			NeqConstraint simultaneousConstraints,
			HashMap<LVar<?>, Unifiable<?>> s) {
		return simultaneousConstraints.getSeparate().toJavaStream()
				.map(t -> t.map(applyOnBoth(Unifiable::getObjectUnifiable)))
				.reduce(Option.of(s),
						(acc, lr) -> acc.flatMap(s1 ->
								// This cannot recurse deeply via verifyUnify
								// because there are no constraints in the package
								unify(Package.empty()
												.withSubstitutions(s1),
										lr._1, lr._2)
										.map(Package::getSubstitutions)),
						throwingBiOp(UnsupportedOperationException::new));
	}

	static Recur<List<NeqConstraint>> walkAllConstraints(
			List<NeqConstraint> constraints,
			Package s) {
		return constraints.toJavaStream()
				.map(c -> walkAllConstraint(s, c.getSeparate())
						.map(java.util.stream.Stream::of))
				.reduce((l, r) -> Recur.zip(l, r)
						.map(lr -> lr.apply(java.util.stream.Stream::concat)))
				.orElseGet(() -> done(java.util.stream.Stream.empty()))
				.map(stream -> stream
						.map(NeqConstraint::of)
						.collect(List.collector()));
	}
	private static Recur<HashMap<LVar<?>, Unifiable<?>>> walkAllConstraint(Package s, HashMap<LVar<?>, Unifiable<?>> c) {
		return c.toJavaStream()
				.map(valSub -> valSub.map(
								val -> walkAll(s, val)
										// this should be right since lhs of a substitution is unbound and unique
										.map(u -> u.asVar().get()),
								sub -> walkAll(s, sub))
						.apply(Recur::zip))
				.reduce(done(HashMap.empty()),
						(acc, v) -> Recur.zip(acc, v)
								.map(ms -> ms.apply(HashMap::put)),
						throwingBiOp(UnsupportedOperationException::new));
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
						.filter(c -> r.isAssociated(c._1) && r.isAssociated(c._2))
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
						throwingBiOp(UnsupportedOperationException::new));
	}

	static Recur<List<NeqConstraint>> removeSubsumed(
			List<NeqConstraint> constraints,
			List<NeqConstraint> constraintAcc) {
		if (constraints.isEmpty()) {
			return done(constraintAcc);
		} else {
			return Recur.zip(
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
