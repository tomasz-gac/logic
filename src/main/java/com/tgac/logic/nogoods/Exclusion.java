package com.tgac.logic.nogoods;

// ABOUTME: The user front door for negative knowledge: exclude states one nogood —
// ABOUTME: "NOT all these literals simultaneously" — through the statement entry.

import com.tgac.logic.constraints.Postable;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.Posting;
import java.util.Arrays;
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

	public static Posting exclude(Postable... literals) {
		Posting[] postings = Arrays.stream(literals)
				.map(Postable::posted)
				.toArray(Posting[]::new);
		Posting forbidden = postings.length == 1 ? postings[0] : Posting.all(postings);
		return Propagation.activate(Nogood.of(forbidden));
	}

}
