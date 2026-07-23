package com.tgac.logic.constraints.store;

// ABOUTME: A store's projected knowledge about a positional var list: entailment
// ABOUTME: via PartialOrder, plus the honesty flag — does this under-state?

import com.tgac.functional.algebra.PartialOrder;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import java.util.List;

/**
 * What {@link Projectable#project} returns: knowledge about a positional var
 * list, comparable by ENTAILMENT ({@code leq}) — which is all any consumer
 * asks: matching is containment (use an entry whose region entails-covers
 * yours), dedup is the leq insert-guard, and a conservative incomparable
 * costs reuse, never soundness (tabled-constraints.md §5.4). Equality is
 * only the degenerate fast path.
 *
 * <p>The residue transcribes EVERYTHING the projection could express —
 * domains and covered couplings alike; what could not be expressed was
 * handled at projection time per the demanded strength
 * ({@link Projectable#project}'s {@code wideningAllowed}).
 */
public interface Residue<R extends Residue<R>> extends PartialOrder<R> {

	/**
	 * Re-impose this knowledge onto live vars, same slots as at projection —
	 * through PUBLIC statement entries, so a replayed residue propagates like
	 * freshly stated knowledge. Carried couplings replay as their live
	 * propagator objects, alias-unified onto the given vars; on the call side
	 * the vars are the originals and the aliasing no-ops.
	 */
	Goal restate(List<Unifiable<?>> vars);
}
