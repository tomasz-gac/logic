package com.tgac.logic.weight;

// ABOUTME: The pure Kleene solver: x = A* ⊗ b over a closed semiring — self-loop,
// ABOUTME: acyclic forward substitution, a real cycle summed, boolean reachability.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Provenance;
import com.tgac.functional.algebra.Semirings;
import io.vavr.collection.Array;
import org.junit.Test;

public class StarSolveTest {

	private static final long INF = Long.MAX_VALUE;

	@Test
	public void selfLoopSumsTheLoopInClosedForm() {
		// loop :- factor(3) | factor(2), loop.  min-plus: 2*⊗3 = 0+3 = 3
		Array<Array<Long>> a = Array.of(Array.of(2L));
		Array<Long> b = Array.of(3L);
		assertThat(StarSolve.solve(Semirings.MIN_PLUS, a, b).toJavaList())
				.containsExactly(3L);
	}

	@Test
	public void acyclicIsForwardSubstitution() {
		// a --5--> b, a the source (base 0): x_a = 0, x_b = 5
		Array<Array<Long>> a = Array.of(
				Array.of(INF, INF),
				Array.of(5L, INF));
		Array<Long> b = Array.of(0L, INF);
		assertThat(StarSolve.solve(Semirings.MIN_PLUS, a, b).toJavaList())
				.containsExactly(0L, 5L);
	}

	@Test
	public void cycleSummedToShortestCosts() {
		// a→b(2), b→c(3), c→a(4); a the source. x = [0, 2, 5]: the round trip
		// (cost 9) never improves a's 0.  (star-tabling.md §3.1)
		Array<Array<Long>> a = Array.of(
				Array.of(INF, INF, 4L),   // x_a = A_ac ⊗ x_c
				Array.of(2L, INF, INF),   // x_b = A_ba ⊗ x_a
				Array.of(INF, 3L, INF));  // x_c = A_cb ⊗ x_b
		Array<Long> b = Array.of(0L, INF, INF);
		assertThat(StarSolve.solve(Semirings.MIN_PLUS, a, b).toJavaList())
				.containsExactly(0L, 2L, 5L);
	}

	@Test
	public void booleanReachabilityOverACycle() {
		// a ↔ b, a reachable (base true): both reachable
		Array<Array<Boolean>> a = Array.of(
				Array.of(false, true),
				Array.of(true, false));
		Array<Boolean> b = Array.of(true, false);
		assertThat(StarSolve.solve(Semirings.BOOLEAN, a, b).toJavaList())
				.containsExactly(true, true);
	}

	@Test
	public void provenanceShowsTheLoopStructure() {
		// a self-loop, coefficient a and base b: x = a* · b — "loop a zero-or-more
		// times, then take base b". min-plus collapsed this to a number; provenance
		// keeps the whole derivation structure visible.
		Provenance a = Provenance.sym("a");
		Provenance base = Provenance.sym("b");
		Array<Array<Provenance>> matrix = Array.of(Array.of(a));
		Provenance x = StarSolve.solve(Semirings.PROVENANCE, matrix, Array.of(base)).get(0);

		assertThat(x.sameLanguage(Provenance.cat(Provenance.star(a), base), 6)).isTrue();
		assertThat(x.sameLanguage(base, 6)).isFalse();
	}
}
