package com.tgac.logic.nogoods;

// ABOUTME: Step-count pins for the exclusion door: four disequality shapes
// ABOUTME: under the deterministic BFS driver, regression pins per shape.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.interpreter.Scope;
import com.tgac.functional.fibers.interpreter.StepListener;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.TestSchedulers;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

/**
 * Step-count pins for disequality through the exclusion door. These four
 * shapes were the Neq kill's paired pins: each ran through Disequality's
 * record store and through {@code exclude(x.unifies(y))} with answer parity
 * asserted, and the gap was the machinery move's target. The record store's
 * final counts, for the ledger: distinct pairs 1202, labelled all-different
 * 1597, rembero 218 (bare recursion; 232 through the nogood door — this pin
 * runs the library {@link Logic#rembero}, whose Matche dispatch and trace
 * label add 12 steps), labelled carve 118 — the nogood door holds within
 * ~3-7% on every shape.
 */
public class ExclusionStepPinsTest {

	private static <T> long steps(Goal goal, Unifiable<T> out) {
		AtomicLong count = new AtomicLong();
		StepListener counting = new StepListener() {
			@Override
			public void onStep(Fiber<?> node, Scope scope, String name) {
				count.incrementAndGet();
			}
		};
		goal.solve(out, fiber -> new BreadthFirstScheduler<>(fiber).withListener(counting))
				.collect(Collectors.toList());
		return count.get();
	}

	private static <T> List<T> answers(Goal g, Unifiable<T> out) {
		return g.solve(out, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());
	}

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	// ---- shape 1: ordered distinct pairs from a menu (membero generation) ----

	@Test
	public void distinctPairs() {
		Unifiable<LList<Integer>> menu = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Goal pairs = unify(menu, LList.ofAll(1, 2, 3, 4, 5))
				.and(exclude(a.unifies(b)))
				.and(Logic.membero(a, menu))
				.and(Logic.membero(b, menu));

		assertThat(answers(pairs, a)).isEqualTo(IntStream.rangeClosed(1, 5)
				.boxed()
				.flatMap(i -> IntStream.range(0, 4).mapToObj(j -> i))
				.collect(Collectors.toList()));
		assertThat(steps(pairs, a)).isEqualTo(1_210L);
	}

	// ---- shape 2: all-different over n vars, labelled (quadratic nogood load) ----

	@Test
	public void labelledAllDifferent() {
		Array<Unifiable<Integer>> vars = Array.fill(4, LVar::lvar);
		Goal g = Goal.success();
		for (int i = 0; i < vars.size(); i++) {
			for (int j = i + 1; j < vars.size(); j++) {
				g = g.and(exclude(vars.get(i).unifies(vars.get(j))));
			}
		}
		for (Unifiable<Integer> v : vars) {
			g = g.and(FiniteDomain.dom(v, dom(1, 2, 3, 4)));
		}

		// 4 vars over 1..4 all-different: 4! = 24 assignments
		assertThat(g.solve(vars.get(0), TestSchedulers.factory()).count()).isEqualTo(24L);
		assertThat(steps(g, vars.get(0))).isEqualTo(1_645L);
	}

	// ---- shape 2b: all-different at scale (the load regimes) ----

	@Test
	public void scaledAllDifferent() {
		// 6 vars over 1..6: 15 resident nogoods, 720 answers — the regime
		// where wholesale re-verification and the subsumption sweep are
		// both quadratic in the store. Baseline (the restored record store,
		// measured at this substrate and deleted again): 63119 — the small-
		// shape ~3% gap is fixed overhead, not a scaling factor (0.2% here)
		Array<Unifiable<Integer>> vars = Array.fill(6, LVar::lvar);
		Goal g = Goal.success();
		for (int i = 0; i < vars.size(); i++) {
			for (int j = i + 1; j < vars.size(); j++) {
				g = g.and(exclude(vars.get(i).unifies(vars.get(j))));
			}
		}
		for (Unifiable<Integer> v : vars) {
			g = g.and(FiniteDomain.dom(v, dom(1, 2, 3, 4, 5, 6)));
		}

		assertThat(g.solve(vars.get(0), TestSchedulers.factory()).count()).isEqualTo(720L);
		assertThat(steps(g, vars.get(0))).isEqualTo(63_239L);
	}

	// ---- shape 2c: subsumption-heavy (the sweep's cost and its benefit) ----

	@Test
	public void subsumptionHeavy() {
		// nine joint nogoods over a 3x3 corner, then three singles that
		// subsume a row each: the store must end minimal (3 nogoods) and
		// the sweep's O(n^2) trials are what this count carries. Baseline
		// (the restored record store, same sweep semantics): 346 — the
		// widest measured gap (~28%), joint registration pays the most
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> p = lvar();
		Goal g = unify(p, lval(Tuple.of(x, y)));
		for (int i = 1; i <= 3; i++) {
			for (int j = 1; j <= 3; j++) {
				g = g.and(exclude(p.unifies(lval(Tuple.of(lval(i), lval(j))))));
			}
		}
		for (int i = 1; i <= 3; i++) {
			g = g.and(exclude(x.unifies(i)));
		}
		g = g.and(FiniteDomain.dom(x, dom(1, 2, 3, 4, 5, 6)))
				.and(FiniteDomain.dom(y, dom(1, 2, 3, 4, 5, 6)));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isEqualTo(18L);
		assertThat(steps(g, x)).isEqualTo(442L);
	}

	// ---- shape 3: rembero (recursion + nogoods) ----

	@Test
	public void remberoRemovesThroughNogoods() {
		Unifiable<LList<Integer>> in = lvar();
		Unifiable<LList<Integer>> out = lvar();
		Goal g = unify(in, LList.ofAll(1, 2, 3, 2, 4))
				.and(Logic.rembero(in, lval(2), out));

		List<String> results = g.solve(out, TestSchedulers.factory())
				.map(Object::toString).sorted().collect(Collectors.toList());
		assertThat(results).containsExactly("{({1}, {3}, {2}, {4})}");
		assertThat(steps(g, out)).isEqualTo(240L);
	}

	// ---- shape 4: the labelled carve (revise-heavy veto) ----

	@Test
	public void labelledCarve() {
		Unifiable<Integer> x = lvar();
		Goal g = FiniteDomain.dom(x, dom(0, 1, 2, 3, 4, 5))
				.and(exclude(x.unifies(3)));

		assertThat(answers(g, x)).containsExactly(0, 1, 2, 4, 5);
		assertThat(steps(g, x)).isEqualTo(124L);
	}
}
