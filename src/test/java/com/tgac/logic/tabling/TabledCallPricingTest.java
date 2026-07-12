package com.tgac.logic.tabling;

// ABOUTME: Pins the ∞→exact pricing transition: a tabled call prices MAX while its
// ABOUTME: entry is incomplete and the exact answer count once the entry completes.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.optimizer.Bounded;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
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

		// run the relation to exhaustion in this package's table, then mark complete
		assertThat(call.solveFrom(p, out, BreadthFirstScheduler::new).count()).isEqualTo(2);
		p.getStore(Table.class).entries().forEach(TableEntry::markComplete);

		assertThat(((Bounded) call).answers(p)).isEqualTo(2);
	}

	@Test
	public void withoutATableThePriceStaysUnbounded() {
		Goal call = smallRelation().apply(Tuple.of(lvar()));
		assertThat(((Bounded) call).answers(Package.empty())).isEqualTo(Long.MAX_VALUE);
	}
}
