package com.tgac.logic.tabling.subsumption;

// ABOUTME: Pins the subsumption map: term-indexed retrieval of stored patterns that
// ABOUTME: generalize the query, with nonlinear-hole precision.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import org.junit.Test;

public class SubsumptionMapTest {

	private static Reified<?> pattern(Object args) {
		return MiniKanren.reify(Substitutions.empty(), lval(args)).get();
	}

	@Test
	public void aGeneralPatternSubsumesItsInstance() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(pattern(Tuple.of(lvar(), lvar())), "general");

		assertThat(map.subsumers(pattern(Tuple.of(lval(1), lvar()))))
				.containsExactly("general");
		assertThat(map.subsumers(pattern(Tuple.of(lval(1), lval(10)))))
				.containsExactly("general");
	}

	@Test
	public void aConcretePositionDoesNotCoverAHole() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(pattern(Tuple.of(lval(1), lvar())), "bound-first");

		// query's first arg is a hole — the stored concrete 1 cannot cover it
		assertThat(map.subsumers(pattern(Tuple.of(lvar(), lval(10)))))
				.isEmpty();
		// different constant — no match either
		assertThat(map.subsumers(pattern(Tuple.of(lval(2), lval(10)))))
				.isEmpty();
		// instance of the stored pattern — found
		assertThat(map.subsumers(pattern(Tuple.of(lval(1), lval(7)))))
				.containsExactly("bound-first");
	}

	@Test
	public void nonlinearHolesDemandEqualSubterms() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		Unifiable<Integer> x = lvar();
		map.put(pattern(Tuple.of(x, x)), "diagonal");

		// structurally the trie path is (VAR, VAR) — the post-filter must
		// reject the unequal pair and accept the equal one
		assertThat(map.subsumers(pattern(Tuple.of(lval(1), lval(2)))))
				.isEmpty();
		assertThat(map.subsumers(pattern(Tuple.of(lval(3), lval(3)))))
				.containsExactly("diagonal");
	}

	@Test
	public void nestedStructureMatchesThroughTheWalk() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		// stored: (X, (5, Y)) — a hole, then a tuple with a constant head
		Unifiable<Object> x = lvar();
		Unifiable<Object> y = lvar();
		map.put(pattern(Tuple.of(x, Tuple.of(lval(5), y))), "nested");

		// the stored first-position hole must skip the query's whole subterm
		assertThat(map.subsumers(pattern(
				Tuple.of(Tuple.of(lval(1), lval(2)), Tuple.of(lval(5), lval(9))))))
				.containsExactly("nested");
		// mismatched constant inside the nested tuple
		assertThat(map.subsumers(pattern(
				Tuple.of(lval(1), Tuple.of(lval(6), lval(9))))))
				.isEmpty();
	}

	@Test
	public void severalGeneralsAllSurface() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(pattern(Tuple.of(lvar(), lvar())), "both-free");
		map.put(pattern(Tuple.of(lval(1), lvar())), "first-bound");

		assertThat(map.subsumers(pattern(Tuple.of(lval(1), lval(10)))))
				.containsExactlyInAnyOrder("both-free", "first-bound");
		assertThat(map.subsumers(pattern(Tuple.of(lval(2), lval(10)))))
				.containsExactly("both-free");
	}

	@Test
	public void alphaEquivalentPatternIsItsOwnSubsumer() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(pattern(Tuple.of(lvar(), lvar())), "self");

		// reflexive: an alpha-equal query is subsumed by the stored pattern;
		// excluding the exact entry is the CALLER's business (reusableSubsumer)
		assertThat(map.subsumers(pattern(Tuple.of(lvar(), lvar()))))
				.containsExactly("self");
	}

	@Test
	public void alphaEquivalentPutsOverwrite() {
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(pattern(Tuple.of(lvar(), lvar())), "first");
		map.put(pattern(Tuple.of(lvar(), lvar())), "second");

		// alpha-equal patterns are ONE key — map semantics, last write wins
		assertThat(map.subsumers(pattern(Tuple.of(lval(1), lval(2)))))
				.containsExactly("second");
	}
}
