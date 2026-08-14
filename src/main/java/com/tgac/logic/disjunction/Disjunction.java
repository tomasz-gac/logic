package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store's front door: anyOf states "at least one of
// ABOUTME: these postings must hold" — order 1 always, no forking is the product.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Disjunction {

	/**
	 * At least one of the alternatives must eventually hold. A single
	 * alternative is a born-unit — the posting itself, nothing stored; an
	 * empty disjunction is a DEAD BRANCH (priced 0, fails on arrival). Doomed
	 * under partial knowledge exactly when every alternative is already
	 * refuted — failure found at pricing is failure forever (refutation is
	 * monotone under binding growth); alternatives whose trials suspend
	 * claim nothing.
	 */
	public static Posting any(Posting... alternatives) {
		List<Posting> flat = Disjunct.flatten(alternatives);
		if (flat.isEmpty()) {
			// nothing can satisfy "at least one of none": the branch is
			// dead, not the program wrong — a chain of ors folded from
			// user data may legitimately come up empty. Eager: ⊥ as a
			// ground clash, failing on arrival and priced 0, no store
			// round trip
			return Posting.bind(lval(false), lval(true));
		}
		if (flat.size() == 1) {
			return flat.head();
		}
		Disjunct disjunct = new Disjunct(flat);
		return Propagation.activate(disjunct, DisjunctionConstraints::register,
				p -> flat.forAll(alternative -> Trial.doomed(alternative, p)));
	}
}
