package com.tgac.logic.nogoods;

// ABOUTME: The user front door for negative knowledge: exclude states one nogood —
// ABOUTME: "NOT all these literals simultaneously" — through the statement entry.

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.Posting;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * States a forbidden conjunction: the nogood is examined at statement (born
 * violated fails, born satisfied discards), re-verified on every revise, and
 * decided at the latest by the ground floor at labelling. Store-agnostic —
 * store front doors already return the statements this door takes —
 * {@code exclude(dom(x, box))} is the negated box, {@code
 * exclude(x.unifies(3), y.unifies(4))} is Neq's record shape.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Exclusion {

	public static Posting exclude(Posting... literals) {
		Posting forbidden = literals.length == 1 ? literals[0] : Posting.all(literals);
		return Propagation.activate(Nogood.of(forbidden), NogoodConstraints::register,
				p -> bornViolated(forbidden, p));
	}

	/**
	 * The doom check, UnifyGoal's dynamic-pricing pattern: a nogood whose
	 * forbidden conjunct is already ENTAILED is born violated — failure found
	 * at pricing is failure forever (entailment is monotone under binding
	 * growth). Binding-shaped conjuncts answer through the synchronous face;
	 * anything store-shaped claims nothing.
	 */
	private static boolean bornViolated(Posting forbidden, com.tgac.logic.goals.Package p) {
		return Trial.now(forbidden, p)
				.map(Trial.Outcome::isEntailed)
				.getOrElse(false);
	}
}
