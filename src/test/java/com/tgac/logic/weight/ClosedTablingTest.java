package com.tgac.logic.weight;

// ABOUTME: The closed (star) tabling path. Wait-mode explore seals like plain
// ABOUTME: tabling, drops escapes, and captures each answer's base weight on the entry.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
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

	@Test
	public void waitModeCapturesBaseWeightOnTheEntry() {
		// loop(1) :- factor(3) | factor(2), loop(1).  The non-looping branch (3) is
		// the base; the looping branch (2) parks on a sleeper. Only the 3 lands as a
		// base weight on the entry.
		Tabled<Tuple1<Unifiable<Integer>>> loop = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.MIN_PLUS, 3L))
						.or(unify(x, lval(1))
								.and(Weights.factor(Semirings.MIN_PLUS, 2L))
								.and(Goal.defer(() -> self.apply(t))))));
		BoundedSemiring<SemiringStore> ring = SemiringStore.boundedProduct(Semirings.MIN_PLUS);
		Table table = closedTable(ring);
		Unifiable<Integer> out = lvar();
		Package root = Package.empty().withStore(table).withStore(ring.one());

		loop.apply(Tuple.of(out)).solveFrom(root, out, BreadthFirstScheduler::new).count();

		TableEntry<Object> entry = table.entries().iterator().next();
		assertThat(entry.baseWeights().values()).hasSize(1);
		SemiringStore base = (SemiringStore) entry.baseWeights().values().iterator().next();
		assertThat(base.get(Semirings.MIN_PLUS)).isEqualTo(3L);
	}

	@SuppressWarnings("unchecked")
	private static Table closedTable(ClosedSemiring<SemiringStore> ring) {
		return Table.closed(
				(ClosedSemiring<Object>) (ClosedSemiring<?>) ring,
				p -> p.getStores().get(SemiringStore.class).getOrElse(ring.one()),
				(p, v) -> p.putStore((SemiringStore) v));
	}
}
