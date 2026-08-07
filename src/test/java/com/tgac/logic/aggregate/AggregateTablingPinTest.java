package com.tgac.logic.aggregate;

// ABOUTME: Pins aggregation over tabling: a consumer of a tabled entry suspends as
// ABOUTME: a frame, so findall folds only when the sub-tree is honestly exhausted.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Aggregation over tabling, correct by construction: a consumer of a tabled
 * entry suspends as a live frame, so a sub-tree containing awaits does not
 * complete until its awaits do — findall's fold (ordinary sequencing after
 * the sub-goal) fires exactly when the collection is complete. These pins
 * asserted the observed 0s while parking read as completion; the await
 * migration flipped them to the true counts.
 */
public class AggregateTablingPinTest {

	private Goal parent(Unifiable<String> x, Unifiable<String> y) {
		return x.unifies("alice").and(y.unifies("bob"))
				.or(x.unifies("bob").and(y.unifies("charlie")))
				.or(x.unifies("charlie").and(y.unifies("david")));
	}

	private final Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> ancestor =
			Tabling.define(args -> args.apply((x, y) ->
					parent(x, y)
							.or(defer(() -> {
								Unifiable<String> z = lvar();
								return parent(x, z).and(ancestor(z, y));
							}))));

	private Goal ancestor(Unifiable<String> x, Unifiable<String> y) {
		return ancestor.apply(Tuple.of(x, y));
	}

	@Test
	public void findallOverAColdSimpleTabledGoal() {
		Tabled<Unifiable<Integer>> rel = Tabling.define(x ->
				x.unifies(1).or(x.unifies(2)));
		Unifiable<LList<Integer>> collected = lvar();

		List<Integer> sizes = Aggregate.findall((Unifiable<Integer> x) -> rel.apply(x), collected)
				.solve(collected, TestSchedulers.factory())
				.map(Term::get)
				.map(l -> (int) l.toValueStream().count())
				.collect(Collectors.toList());

		// the true answer: bob's descendants are charlie and david — the
		// consumer suspends instead of parking, so the fold waits for them
		assertThat(sizes).containsExactly(2);
	}

	@Test
	public void findallOverAColdRecursiveTabledGoal() {
		Unifiable<LList<String>> collected = lvar();

		List<Integer> sizes = Aggregate.findall(
						(Unifiable<String> who) -> ancestor(lval("alice"), who), collected)
				.solve(collected, TestSchedulers.factory())
				.map(Term::get)
				.map(l -> (int) l.toValueStream().count())
				.collect(Collectors.toList());

		// the true answer: alice's descendants are bob, charlie, david — the
		// recursive table exhausts before the fold fires
		assertThat(sizes).containsExactly(3);
	}

	@Test
	public void countOverAColdTabledGoal() {
		Unifiable<Integer> n = lvar();

		List<Integer> counts = Aggregate.count((Unifiable<String> who) -> ancestor(lval("alice"), who), n)
				.solve(n, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());

		// the true count: the sub-tree completes only when the table is
		// exhausted, so count over a tabled goal is negation-safe
		assertThat(counts).containsExactly(3);
	}
}
