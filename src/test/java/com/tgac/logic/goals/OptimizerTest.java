package com.tgac.logic.goals;

// ABOUTME: Pins the Optimizer seam: cascading normalization flattens nested and/or
// ABOUTME: in one pass, is transparent to NamedGoal, and treats Guard/opaque goals as leaves.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.monad.Cont;
import com.tgac.logic.unification.Unifiable;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;

public class OptimizerTest {

	private static Goal leaf() {
		return s -> Cont.just(s);
	}

	private static Goal cascade(Goal g) {
		return g.accept(new CascadingOptimizer()).get();
	}

	@Test
	public void flattensNestedConjunctionsInOnePass() {
		Goal a = leaf(), b = leaf(), c = leaf();
		Goal flat = cascade(a.and(b.and(c)));
		assertThat(flat).isInstanceOf(Conjunction.class);
		assertThat(((Conjunction) flat).getClauses()).containsExactly(a, b, c);
	}

	@Test
	public void flattensNestedDisjunctionsIntoSiblingAlternatives() {
		Goal a = leaf(), b = leaf(), c = leaf();
		Goal flat = cascade(a.or(b.or(c)));
		assertThat(flat).isInstanceOf(Conde.class);
		assertThat(((Conde) flat).getClauses()).containsExactly(a, b, c);
	}

	@Test
	public void opaqueGoalsAreLeaves() {
		Goal lambda = leaf();
		assertThat(cascade(lambda)).isSameAs(lambda);

		Goal committed = new Conda().orElseFirst(leaf()).orElseFirst(leaf());
		assertThat(cascade(committed)).isSameAs(committed);
	}

	@Test
	public void namedGoalsAreTransparent() {
		Goal a = leaf(), b = leaf(), c = leaf();
		Function<Package, String> label = s -> "query";
		Goal optimized = cascade(NamedGoal.of(label, a.and(b.and(c))));

		assertThat(optimized).isInstanceOf(NamedGoal.class);
		assertThat(((NamedGoal) optimized).getLabel()).isSameAs(label);
		assertThat(((NamedGoal) optimized).getGoal()).isInstanceOf(Conjunction.class);
		assertThat(((Conjunction) ((NamedGoal) optimized).getGoal()).getClauses())
				.containsExactly(a, b, c);
	}

	@Test
	public void guardIsALeafTheOptimizerNeverEnters() {
		Goal a = leaf(), b = leaf(), c = leaf();
		Goal deliberate = a.and(b.and(c));
		Guard guard = Guard.of(deliberate);
		assertThat(cascade(guard)).isSameAs(guard);
		assertThat(guard.getGoal()).isSameAs(deliberate);
	}

	@Test
	public void sameOptimizerUnwrapsAndFusesNestedOptimized() {
		Goal a = leaf(), b = leaf(), c = leaf();
		CascadingOptimizer optimizer = new CascadingOptimizer();
		// a wrapped subtree fuses into the surrounding plan: no wrapper survives,
		// and its clauses flatten into the outer conjunction
		Goal flat = a.and(b.and(c).optimize(optimizer)).accept(optimizer).get();
		assertThat(flat).isInstanceOf(Conjunction.class);
		assertThat(((Conjunction) flat).getClauses()).containsExactly(a, b, c);
	}

	@Test
	public void foreignOptimizedIsALeaf() {
		Goal a = leaf(), b = leaf(), c = leaf();
		Goal foreign = b.and(c).optimize(new CascadingOptimizer());
		Goal walked = a.and(foreign).accept(new CascadingOptimizer()).get();
		assertThat(((Conjunction) walked).getClauses()).containsExactly(a, foreign);
	}

	@Test
	public void optimizedGoalSolvesToTheSameAnswers() {
		Unifiable<Integer> x = lvar();
		Goal g = unify(x, lval(3)).and(Goal.success().and(Goal.success()));
		assertThat(g.optimize(new CascadingOptimizer()).solve(x)
				.map(Object::toString)
				.collect(Collectors.toList()))
				.isEqualTo(g.solve(x)
						.map(Object::toString)
						.collect(Collectors.toList()));
	}
}
