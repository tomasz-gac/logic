package com.tgac.logic.constraints.store;

// ABOUTME: A store's projected knowledge about a positional var list: entailment
// ABOUTME: via PartialOrder, plus the honesty flag — does this under-state?

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import java.util.List;

/**
 * What {@link Projectable#project} returns: knowledge about a positional var
 * list, comparable by entailment ({@code leq}), and HONEST about its own
 * limits — {@link #isWidened} says whether live knowledge about the supplied
 * vars could not be expressed (a coupling outside the vocabulary, or one
 * escaping to an unsupplied local). The store reports; the BOUNDARY refuses,
 * with per-side reasons (tabled-constraints.md §5.1): on answers a widened
 * residue replays wrong answers (nothing re-filters) — refusal is necessary;
 * on calls dropping is sound by containment (the master searches wider, the
 * caller filters) — refusal is chosen strictness, and acceptance is the
 * call-abstraction knob.
 *
 * <p>WIDENED IS ADVISORY, NOT IDENTITY: two residues stating the same region
 * are equal regardless of the flag — keys name what the master searches, and
 * callers widened to the same region should share an entry.
 */
public interface Residue<R extends Residue<R>> extends PartialOrder<R> {

	boolean isWidened();

	/**
	 * Re-impose this knowledge onto live vars, same slots as at projection —
	 * through PUBLIC statement entries (constraint posts), so a replayed
	 * residue propagates like freshly stated knowledge. The residue restates
	 * ITSELF: it is self-describing (its slots, its values, its recipes) and
	 * needs the public factories, not the store instance that projected it.
	 */
	Goal restate(List<Unifiable<?>> vars);
}
