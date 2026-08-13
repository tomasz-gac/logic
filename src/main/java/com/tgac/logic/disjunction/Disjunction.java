package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store's front door: anyOf states "at least one of
// ABOUTME: these postings must hold" — order 1 always, no forking is the product.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.goals.Package;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Disjunction {

	/**
	 * At least one of the alternatives must eventually hold. A single
	 * alternative is a born-unit — the posting itself, nothing stored; an
	 * empty disjunction is unsatisfiable and refuses at the door. Doomed
	 * under partial knowledge exactly when every alternative is already
	 * refuted — failure found at pricing is failure forever (refutation is
	 * monotone under binding growth); alternatives whose trials suspend
	 * claim nothing.
	 */
	public static Posting anyOf(Posting... alternatives) {
		List<Posting> flat = Disjunct.flatten(alternatives);
		if (flat.isEmpty()) {
			throw new IllegalArgumentException(
					"an empty disjunction is unsatisfiable — nothing to require");
		}
		if (flat.size() == 1) {
			return flat.head();
		}
		Disjunct disjunct = new Disjunct(flat);
		return Propagation.activate(disjunct, DisjunctionConstraints::register,
				p -> bornRefuted(flat, p));
	}

	private static boolean bornRefuted(List<Posting> alternatives, Package p) {
		return alternatives.forAll(alternative -> {
			Fiber<Trial.Outcome> trial = Trial.trial(alternative, p);
			return trial.isDone() && trial.get().isRefuted();
		});
	}
}
