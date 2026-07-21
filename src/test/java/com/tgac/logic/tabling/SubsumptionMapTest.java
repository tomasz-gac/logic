package com.tgac.logic.tabling;

// ABOUTME: Pins the subsumption map: term-indexed retrieval of stored calls whose
// ABOUTME: pattern generalizes the query, with nonlinear-hole precision.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.Test;

public class SubsumptionMapTest {

	private static Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> relation() {
		return Tabling.define(t -> Goal.success());
	}

	private static Reified<?> pattern(Object args) {
		return MiniKanren.reify(Substitutions.empty(), lval(args)).get();
	}

	private static Call call(Tabled<?> rel, Object args) {
		return Call.of(rel, pattern(args));
	}

	@Test
	public void aGeneralPatternSubsumesItsInstance() {
		Tabled<?> rel = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(call(rel, Tuple.of(lvar(), lvar())), "general");

		assertThat(map.subsumers(call(rel, Tuple.of(lval(1), lvar()))))
				.containsExactly("general");
		assertThat(map.subsumers(call(rel, Tuple.of(lval(1), lval(10)))))
				.containsExactly("general");
	}

	@Test
	public void aConcretePositionDoesNotCoverAHole() {
		Tabled<?> rel = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(call(rel, Tuple.of(lval(1), lvar())), "bound-first");

		// query's first arg is a hole — the stored concrete 1 cannot cover it
		assertThat(map.subsumers(call(rel, Tuple.of(lvar(), lval(10)))))
				.isEmpty();
		// different constant — no match either
		assertThat(map.subsumers(call(rel, Tuple.of(lval(2), lval(10)))))
				.isEmpty();
		// instance of the stored pattern — found
		assertThat(map.subsumers(call(rel, Tuple.of(lval(1), lval(7)))))
				.containsExactly("bound-first");
	}

	@Test
	public void nonlinearHolesDemandEqualSubterms() {
		Tabled<?> rel = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		Unifiable<Integer> x = lvar();
		map.put(call(rel, Tuple.of(x, x)), "diagonal");

		// structurally the trie path is (VAR, VAR) — the post-filter must
		// reject the unequal pair and accept the equal one
		assertThat(map.subsumers(call(rel, Tuple.of(lval(1), lval(2)))))
				.isEmpty();
		assertThat(map.subsumers(call(rel, Tuple.of(lval(3), lval(3)))))
				.containsExactly("diagonal");
	}

	@Test
	public void relationsNeverShareEntries() {
		Tabled<?> a = relation();
		Tabled<?> b = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(call(a, Tuple.of(lvar(), lvar())), "a");

		assertThat(map.subsumers(call(b, Tuple.of(lval(1), lval(2)))))
				.isEmpty();
		assertThat(map.subsumers(call(a, Tuple.of(lval(1), lval(2)))))
				.containsExactly("a");
	}

	@Test
	public void nestedStructureMatchesThroughTheWalk() {
		Tabled<?> rel = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		// stored: (X, (5, Y)) — a hole, then a tuple with a constant head
		Unifiable<Object> x = lvar();
		Unifiable<Object> y = lvar();
		map.put(call(rel, Tuple.of(x, Tuple.of(lval(5), y))), "nested");

		// the stored first-position hole must skip the query's whole subterm
		assertThat(map.subsumers(call(rel,
				Tuple.of(Tuple.of(lval(1), lval(2)), Tuple.of(lval(5), lval(9))))))
				.containsExactly("nested");
		// mismatched constant inside the nested tuple
		assertThat(map.subsumers(call(rel,
				Tuple.of(lval(1), Tuple.of(lval(6), lval(9))))))
				.isEmpty();
	}

	@Test
	public void severalGeneralsAllSurface() {
		Tabled<?> rel = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(call(rel, Tuple.of(lvar(), lvar())), "both-free");
		map.put(call(rel, Tuple.of(lval(1), lvar())), "first-bound");

		assertThat(map.subsumers(call(rel, Tuple.of(lval(1), lval(10)))))
				.containsExactlyInAnyOrder("both-free", "first-bound");
		assertThat(map.subsumers(call(rel, Tuple.of(lval(2), lval(10)))))
				.containsExactly("both-free");
	}

	@Test
	public void alphaEquivalentPatternIsItsOwnSubsumer() {
		Tabled<?> rel = relation();
		SubsumptionMap<String> map = new SubsumptionMap<>();
		map.put(call(rel, Tuple.of(lvar(), lvar())), "self");

		// reflexive: an alpha-equal query is subsumed by the stored pattern;
		// excluding the exact entry is the CALLER's business (reusableSubsumer)
		assertThat(map.subsumers(call(rel, Tuple.of(lvar(), lvar()))))
				.containsExactly("self");
	}
}
