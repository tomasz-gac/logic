package com.tgac.logic.weight;

// ABOUTME: The closed (star) tabling path. Phase 2: wait-mode explore runs and
// ABOUTME: seals like plain tabling, and every exploration escape is dropped.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Unifiable;
import org.junit.Test;

public class ClosedTablingTest {

	@Test
	public void waitModeExploreDropsEveryEscape() {
		// a two-fact tabled relation. solveBounded streams its answers; solveClosed
		// explores and seals identically but DROPS every escape as an exploration
		// fragment (emit is a later phase), so it yields nothing yet.
		Tabled<Unifiable<String>> rel = Tabling.define(x ->
				unify(x, lval("a")).or(unify(x, lval("b"))));
		BoundedSemiring<SemiringStore> ring = SemiringStore.boundedProduct(Semirings.MIN_PLUS);

		Unifiable<String> streamed = lvar();
		assertThat(Weights.solveBounded(rel.apply(streamed), streamed, ring, BreadthFirstScheduler::new)
				.count()).isEqualTo(2);

		Unifiable<String> closed = lvar();
		assertThat(Weights.solveClosed(rel.apply(closed), closed, ring, BreadthFirstScheduler::new)
				.count()).isEqualTo(0);
	}
}
