package com.tgac.logic.nogoods;

// ABOUTME: Live nogoods render as answer residuals through Constrained — the ¬
// ABOUTME: format by toString delegation, invisible names pruned, Neq's discipline.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class NogoodRenderingTest {

	private static <T> List<String> rendered(Goal g, Unifiable<T> out) {
		return g.solve(out, TestSchedulers.factory())
				.map(Object::toString)
				.collect(Collectors.toList());
	}

	@Test
	public void aLiveBindNogoodRendersAsAResidual() {
		Unifiable<Integer> x = lvar();

		assertThat(rendered(exclude(x.unifies(3)), x))
				.containsExactly("_.0 : ¬(_.0 ≣ {3})");
	}

	@Test
	public void aJointNogoodRendersItsLiteralsConjoined() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> out = lval(Tuple.of(x, y));

		List<String> answers = rendered(exclude(x.unifies(3), y.unifies(4)), out);

		assertThat(answers).hasSize(1);
		assertThat(answers.get(0))
				.contains("_.0 ≣ {3}")
				.contains("_.1 ≣ {4}")
				.contains("¬(");
	}

	@Test
	public void aNogoodAboutAnUnrenderedLocalStaysInvisible() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> hidden = lvar();

		assertThat(rendered(exclude(hidden.unifies(3)), x))
				.containsExactly("_.0");
	}

	@Test
	public void aDischargedNogoodLeavesNoResidual() {
		Unifiable<Integer> x = lvar();

		assertThat(rendered(exclude(x.unifies(3)).and(x.unifies(5)), x))
				.containsExactly("{5}");
	}

	@Test
	public void aStoreContentNogoodRendersThroughItsOwnToString() {
		// the wall retired entirely: the negated box is renderable by
		// delegation, no store-specific display code
		Unifiable<Long> x = lvar();

		List<String> answers = rendered(exclude(dom(x, EnumeratedDomain.range(2L, 5L))), x);

		assertThat(answers).hasSize(1);
		assertThat(answers.get(0)).startsWith("_.0 : ¬(");
		assertThat(answers.get(0)).contains("⊂");
	}

	@Test
	public void aVarVarNogoodRendersBothSides() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> out = lval(Tuple.of(x, y));

		assertThat(rendered(exclude(x.unifies(y)), out))
				.containsExactly("{(_.0, _.1)} : ¬(_.0 ≣ _.1)");
	}

	@Test
	public void tabledConditionalAnswersRenderAtTheCaller() {
		Tabled<Unifiable<Integer>> notThree =
				Tabling.define(x -> exclude(x.unifies(3)));
		Unifiable<Integer> y = lvar();

		assertThat(rendered(notThree.apply(y), y))
				.containsExactly("_.0 : ¬(_.0 ≣ {3})");
	}

	@Test
	public void aSharedNameAcrossKeptLiteralsRendersOnce() {
		// y appears in BOTH surviving literals (¬(x=y ∧ x=z) simplifies to
		// x↦y and y↦z) — the display map must tolerate the duplicate, since
		// walk(name) is deterministic and both occurrences agree
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Unifiable<io.vavr.Tuple3<Unifiable<Integer>, Unifiable<Integer>, Unifiable<Integer>>> out =
				lval(io.vavr.Tuple.of(x, y, z));

		List<String> answers = rendered(exclude(x.unifies(y), x.unifies(z)), out);

		assertThat(answers).hasSize(1);
		assertThat(answers.get(0)).contains("¬(");
	}

	@Test
	public void theCarrierRefusesTheFold() {
		// isGround false by meaning: a conditional answer denotes a region,
		// so everything demanding values must refuse or enforce first
		Unifiable<Integer> x = lvar();

		List<Reified<Integer>> answers = exclude(x.unifies(3))
				.solve(x, TestSchedulers.factory())
				.collect(Collectors.toList());

		assertThat(answers).hasSize(1);
		assertThat(answers.get(0).isGround()).isFalse();
	}
}
