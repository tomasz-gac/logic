package com.tgac.logic.aggregate;

// ABOUTME: Pins aggregation over tabling: findall folds on fiber-tree completion,
// ABOUTME: which a parking consumer satisfies before the table is exhausted.

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
 * The completion caveat the {@link Aggregate} javadoc confesses, measured: a
 * consumer of a tabled entry completes its FIBER by parking, so findall's
 * exhaustion criterion (fiber-tree completion) can fire while the master still
 * owes answers — the fold reads a partial set. The fix (Scope: fold on seal)
 * flips these pins to the true counts.
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
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> collected = lvar();

		List<Integer> sizes = Aggregate.findall(x, rel.apply(x), collected)
				.solve(collected)
				.map(Term::get)
				.map(l -> (int) l.toValueStream().count())
				.collect(Collectors.toList());

		// DEFECT PINNED: the true answer is 2. The consumer parks before the
		// detached master produces anything, findall's fiber tree completes,
		// and the fold fires on the empty set — deterministically. Flips to 2
		// when aggregation folds on the scope seal.
		assertThat(sizes).containsExactly(0);
	}

	@Test
	public void findallOverAColdRecursiveTabledGoal() {
		Unifiable<String> who = lvar();
		Unifiable<LList<String>> collected = lvar();

		List<Integer> sizes = Aggregate.findall(who,
						ancestor(lval("alice"), who), collected)
				.solve(collected)
				.map(Term::get)
				.map(l -> (int) l.toValueStream().count())
				.collect(Collectors.toList());

		// DEFECT PINNED: alice's descendants are bob, charlie, david — the
		// true answer is 3. Same parking mechanics as the simple case. Flips
		// to 3 when aggregation folds on the scope seal.
		assertThat(sizes).containsExactly(0);
	}

	@Test
	public void countOverAColdTabledGoal() {
		Unifiable<Integer> n = lvar();
		Unifiable<String> who = lvar();

		List<Integer> counts = Aggregate.count(ancestor(lval("alice"), who), n)
				.solve(n)
				.map(Term::get)
				.collect(Collectors.toList());

		// DEFECT PINNED: the true count is 3; count() shares findall's
		// exhaustion criterion and reads 0. The dangerous face of the same
		// bug: 0 is negation's answer ("no ancestors"), silently wrong.
		// Flips to 3 when aggregation folds on the scope seal.
		assertThat(counts).containsExactly(0);
	}
}
