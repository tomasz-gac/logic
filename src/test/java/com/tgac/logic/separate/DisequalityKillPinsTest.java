package com.tgac.logic.separate;

// ABOUTME: Paired step-count pins for the Neq kill: each shape via Disequality
// ABOUTME: and via exclude(unifies), answer parity asserted, the gap is the target.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.interpreter.StepListener;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The kill's cut 0: every shape twice — the Neq door and the nogood door —
 * with answer parity asserted between the twins and both step counts pinned
 * under the deterministic BFS driver. The exclude-minus-separate gap per
 * shape is the machinery move's target; after the reroute the separate pins
 * become the exclude pins and the gap must be gone.
 */
public class DisequalityKillPinsTest {

	private static <T> long steps(Goal goal, Unifiable<T> out) {
		AtomicLong count = new AtomicLong();
		StepListener counting = new StepListener() {
			@Override
			public void onStep(Fiber<?> node) {
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

	private static Goal pairsVia(java.util.function.BiFunction<Unifiable<Integer>, Unifiable<Integer>, Goal> neq,
			Unifiable<Integer> a, Unifiable<Integer> b) {
		Unifiable<LList<Integer>> menu = lvar();
		return unify(menu, LList.ofAll(1, 2, 3, 4, 5))
				.and(neq.apply(a, b))
				.and(Logic.membero(a, menu))
				.and(Logic.membero(b, menu));
	}

	@Test
	public void distinctPairs() {
		Unifiable<Integer> a1 = lvar();
		Unifiable<Integer> b1 = lvar();
		Goal viaSeparate = pairsVia(Disequality::separate, a1, b1);
		Unifiable<Integer> a2 = lvar();
		Unifiable<Integer> b2 = lvar();
		Goal viaExclude = pairsVia((x, y) -> exclude(x.unifies(y)), a2, b2);

		assertThat(answers(viaExclude, a2)).isEqualTo(answers(viaSeparate, a1));
		assertThat(steps(viaSeparate, a1)).isEqualTo(1_202L);
		assertThat(steps(viaExclude, a2)).isEqualTo(4_016L);
	}

	// ---- shape 2: distincto over n vars, labelled (quadratic record load) ----

	private static Goal allDistinctVia(java.util.function.BiFunction<Unifiable<Integer>, Unifiable<Integer>, Goal> neq,
			Array<Unifiable<Integer>> vars) {
		Goal g = Goal.success();
		for (int i = 0; i < vars.size(); i++) {
			for (int j = i + 1; j < vars.size(); j++) {
				g = g.and(neq.apply(vars.get(i), vars.get(j)));
			}
		}
		for (Unifiable<Integer> v : vars) {
			g = g.and(FiniteDomain.dom(v, dom(1, 2, 3, 4)));
		}
		return g;
	}

	@Test
	public void labelledAllDifferent() {
		Array<Unifiable<Integer>> vs1 = Array.fill(4, com.tgac.logic.unification.LVar::lvar);
		Goal viaSeparate = allDistinctVia(Disequality::separate, vs1);
		Array<Unifiable<Integer>> vs2 = Array.fill(4, com.tgac.logic.unification.LVar::lvar);
		Goal viaExclude = allDistinctVia((x, y) -> exclude(x.unifies(y)), vs2);

		// 4 vars over 1..4 all-different: 4! = 24 assignments
		assertThat(viaSeparate.solve(vs1.get(0), TestSchedulers.factory()).count()).isEqualTo(24L);
		assertThat(viaExclude.solve(vs2.get(0), TestSchedulers.factory()).count()).isEqualTo(24L);
		assertThat(steps(viaSeparate, vs1.get(0))).isEqualTo(1_597L);
		// FINDING (Aug 2026): the exclude side varies ACROSS JVM runs under the
		// deterministic driver — sampled {18269, 18685, 19309} — so some loop
		// in the package-trial path iterates in identity-hash order. The
		// bind fast path replaces that path; re-measure after it lands and
		// either the variance dies with the code or it gets root-caused then.
		// An exact pin is owed here.
		assertThat(steps(viaExclude, vs2.get(0))).isBetween(17_500L, 20_500L);
	}

	// ---- shape 3: rembero (recursion + records) ----

	private static <T> Goal remberoVia(java.util.function.BiFunction<Unifiable<T>, Unifiable<T>, Goal> neq,
			Unifiable<LList<T>> ls, Unifiable<T> x, Unifiable<LList<T>> out) {
		Unifiable<LList<T>> d = lvar();
		Unifiable<T> a = lvar();
		Unifiable<LList<T>> res = lvar();
		return unify(ls, LList.empty()).and(unify(out, LList.empty()))
				.or(unify(ls, LList.of(x, d)).and(unify(out, d)))
				.or(unify(ls, LList.of(a, d)).and(neq.apply(a, x)).and(unify(out, LList.of(a, res)))
						.and(Goal.defer(() -> remberoVia(neq, d, x, res))));
	}

	@Test
	public void remberoRemovesThroughRecords() {
		Unifiable<LList<Integer>> in1 = lvar();
		Unifiable<LList<Integer>> out1 = lvar();
		Goal viaSeparate = unify(in1, LList.ofAll(1, 2, 3, 2, 4))
				.and(remberoVia(Disequality::separate, in1, lval(2), out1));
		Unifiable<LList<Integer>> in2 = lvar();
		Unifiable<LList<Integer>> out2 = lvar();
		Goal viaExclude = unify(in2, LList.ofAll(1, 2, 3, 2, 4))
				.and(remberoVia((x, y) -> exclude(x.unifies(y)), in2, lval(2), out2));

		List<String> a1 = viaSeparate.solve(out1, TestSchedulers.factory())
				.map(Object::toString).sorted().collect(Collectors.toList());
		List<String> a2 = viaExclude.solve(out2, TestSchedulers.factory())
				.map(Object::toString).sorted().collect(Collectors.toList());
		assertThat(a2).isEqualTo(a1);
		assertThat(steps(viaSeparate, out1)).isEqualTo(218L);
		assertThat(steps(viaExclude, out2)).isEqualTo(436L);
	}

	// ---- shape 4: the labelled carve (revise-heavy veto) ----

	@Test
	public void labelledCarve() {
		Unifiable<Integer> x1 = lvar();
		Goal viaSeparate = FiniteDomain.dom(x1, dom(0, 1, 2, 3, 4, 5))
				.and(Disequality.separate(x1, lval(3)));
		Unifiable<Integer> x2 = lvar();
		Goal viaExclude = FiniteDomain.dom(x2, dom(0, 1, 2, 3, 4, 5))
				.and(exclude(x2.unifies(3)));

		assertThat(answers(viaExclude, x2)).isEqualTo(answers(viaSeparate, x1));
		assertThat(steps(viaSeparate, x1)).isEqualTo(118L);
		assertThat(steps(viaExclude, x2)).isEqualTo(456L);
	}
}
