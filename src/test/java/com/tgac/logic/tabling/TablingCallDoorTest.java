package com.tgac.logic.tabling;

// ABOUTME: The public tabled-call door: any value-equal token keys the solve's
// ABOUTME: table, bodies ride the call, method recursion re-enters and seals.

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * {@link Tabling#call} generalizes the door {@link Tabled#apply} always used:
 * the relation slot takes ANY identity token, keyed by value — two equal
 * tokens are one relation, production runs once, and a method that re-enters
 * the door recursively is an ordinary consumer completion detection seals.
 */
public class TablingCallDoorTest {

	private static Goal edge(int[][] edges, Unifiable<Integer> x, Unifiable<Integer> y) {
		return Arrays.stream(edges)
				.map(e -> (Goal) x.unifies(e[0]).and(y.unifies(e[1])))
				.reduce(Goal::or)
				.orElseGet(Goal::failure);
	}

	@Test(timeout = 5000)
	public void aForeignTokenTables() {
		int[][] edges = {{1, 2}, {1, 3}};
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		List<String> answers = x.unifies(1)
				.and(Tabling.call("edges", Tuple.of(x, y), () -> edge(edges, x, y)))
				.solve(y, TestSchedulers.factory())
				.map(Object::toString).sorted().collect(Collectors.toList());
		assertThat(answers).containsExactly("{2}", "{3}");
	}

	@Test(timeout = 5000)
	public void valueEqualTokensShareOneProduction() {
		int[][] edges = {{1, 2}, {1, 3}};
		AtomicInteger productions = new AtomicInteger();
		// distinct INSTANCES, equal by value — the sharing must come from
		// equals, not interning; the pin below keeps the distinction honest
		Object token1 = Tuple.of("r");
		Object token2 = Tuple.of("r");
		assertThat(token1).isNotSameAs(token2).isEqualTo(token2);
		Unifiable<Integer> one = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		long count = one.unifies(1)
				.and(Tabling.call(token1, Tuple.of(one, a), () -> {
					productions.incrementAndGet();
					return edge(edges, one, a);
				}))
				.and(Tabling.call(token2, Tuple.of(one, b), () -> {
					productions.incrementAndGet();
					return edge(edges, one, b);
				}))
				.solve(lvar(), TestSchedulers.factory())
				.count();
		assertThat(count).isEqualTo(4);
		assertThat(productions.get()).isEqualTo(1);
	}

	@Test(timeout = 5000)
	public void distinctTokensKeepSeparateCaches() {
		AtomicInteger productions = new AtomicInteger();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		long count = Tabling.call("p", Tuple.of(a), () -> {
					productions.incrementAndGet();
					return a.unifies(1);
				})
				.and(Tabling.call("q", Tuple.of(b), () -> {
					productions.incrementAndGet();
					return b.unifies(2);
				}))
				.solve(lvar(), TestSchedulers.factory())
				.count();
		assertThat(count).isEqualTo(1);
		assertThat(productions.get()).isEqualTo(2);
	}

	/** The residence arc's target shape: recursion by calling the METHOD. */
	private Goal reach(int[][] edges, Unifiable<Integer> x, Unifiable<Integer> y) {
		return Tabling.call("reach", Tuple.of(x, y), () ->
				edge(edges, x, y)
						.or(defer(() -> {
							Unifiable<Integer> z = lvar();
							return reach(edges, x, z).and(edge(edges, z, y));
						})));
	}

	@Test(timeout = 5000)
	public void methodRecursionThroughTheDoorSealsACycle() {
		int[][] cycle = {{1, 2}, {2, 3}, {3, 1}};
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		List<String> answers = x.unifies(1).and(reach(cycle, x, y))
				.solve(y, TestSchedulers.factory())
				.map(Object::toString).sorted().collect(Collectors.toList());
		assertThat(answers).containsExactly("{1}", "{2}", "{3}");
	}
}
