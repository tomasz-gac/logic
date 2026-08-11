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
 * stores expose their own literal wrappers (e.g. {@code FiniteDomain.in})
 * and sugar doors over this one (e.g. {@code FiniteDomain.notin}).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Exclusion {

	public static Statement exclude(Statement... literals) {
		return Propagation.activate(Nogood.of(List.of(literals)), Nogoods::register, p -> false);
	}
}
