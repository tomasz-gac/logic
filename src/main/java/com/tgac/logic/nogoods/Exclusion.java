package com.tgac.logic.nogoods;

// ABOUTME: The user front door for negative knowledge: exclude states one nogood —
// ABOUTME: "NOT all these literals simultaneously" — through the statement entry.

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Statement;
import io.vavr.collection.List;
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

	public static Statement exclude(Statement... literals) {
		return Propagation.activate(Nogood.of(List.of(literals)), Nogoods::register, p -> false);
	}
}
