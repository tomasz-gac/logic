package com.tgac.logic.goals.optimizer;

// ABOUTME: Law-tests the shipped pricers along growing-knowledge chains: a price
// ABOUTME: must never rise as knowledge grows — stale prices stay sound.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.MonotoneLaws;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.FiniteDomainTestSupport;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import java.util.Arrays;
import java.util.function.BiPredicate;
import org.junit.Test;

public class PricerMonotonicityTest {

	/** counts ordered downward: more knowledge in, fewer-or-equal answers out */
	private static final BiPredicate<Long, Long> NEVER_RISES = (lo, hi) -> lo >= hi;

	@Test
	public void domPostPriceNeverRises() {
		Unifiable<Long> x = lvar();
		Goal post = FiniteDomain.dom(x, Interval.of(8L, 12L));
		Package blind = Package.empty();
		Package overlapping = FiniteDomainTestSupport.withDomain(x, Interval.of(0L, 10L));
		Package disjoint = FiniteDomainTestSupport.withDomain(x, Interval.of(0L, 4L));
		MonotoneLaws.check(
				Arrays.asList(blind, overlapping, disjoint),
				p -> ((Bounded) post).answers(p),
				NEVER_RISES);
	}

	@Test
	public void tabledCallPriceNeverRises() {
		Tabled<Tuple1<Unifiable<Integer>>> rel = Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).or(unify(x, lval(2)))));
		Unifiable<Integer> out = lvar();
		Goal call = rel.apply(Tuple.of(out));

		Package noTable = Package.empty();
		Package incomplete = Package.empty().withStore(Table.empty());
		Package complete = Package.empty().withStore(Table.empty());
		assertThat(call.solveFrom(complete, out, BreadthFirstScheduler::new).count()).isEqualTo(2);
		complete.getStore(Table.class).entries().forEach(TableEntry::markComplete);

		MonotoneLaws.check(
				Arrays.asList(noTable, incomplete, complete),
				p -> ((Bounded) call).answers(p),
				NEVER_RISES);
	}
}
