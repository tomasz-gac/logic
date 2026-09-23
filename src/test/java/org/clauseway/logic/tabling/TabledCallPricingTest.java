package org.clauseway.logic.tabling;

// ABOUTME: Pins the ∞→exact pricing transition: a tabled call prices MAX while its
// ABOUTME: entry is incomplete and the exact answer count once the entry completes.

import static org.clauseway.logic.constraints.Constraints.unify;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.goals.optimizer.Bounded;
import org.clauseway.logic.unification.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple1;
import org.junit.Test;

public class TabledCallPricingTest {

	private static Tabled<Tuple1<Unifiable<Integer>>> smallRelation() {
		return Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).or(unify(x, lval(2)))));
	}

	@Test
	public void incompleteEntryPricesUnbounded() {
		Goal call = smallRelation().apply(Tuple.of(lvar()));
		Package p = Package.empty().withStore(Table.empty());
		assertThat(((Bounded) call).answers(p)).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	public void completedEntryPricesItsAnswerCount() {
		Tabled<Tuple1<Unifiable<Integer>>> rel = smallRelation();
		Unifiable<Integer> out = lvar();
		Goal call = rel.apply(Tuple.of(out));
		Package p = Package.empty().withStore(Table.empty());

		// run the relation to exhaustion in this package's table: the full
		// drain seals the entries through completion detection
		assertThat(call.solveFrom(p, out, BreadthFirstScheduler::new).count()).isEqualTo(2);

		assertThat(((Bounded) call).answers(p)).isEqualTo(2);
	}

	@Test
	public void withoutATableThePriceStaysUnbounded() {
		Goal call = smallRelation().apply(Tuple.of(lvar()));
		assertThat(((Bounded) call).answers(Package.empty())).isEqualTo(Long.MAX_VALUE);
	}
}
