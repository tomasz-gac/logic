package com.tgac.logic.algebra;

// ABOUTME: Semiring laws for the goal witnesses up to answer equality —
// ABOUTME: multiset Eq for DERIVATIONS, set Eq for ANSWERS (the dedup quotient).

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.IdempotentSemiringLaws;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemiringLaws;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.GoalSemirings;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(GoalSemirings.class)
public class GoalSemiringLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(GoalSemiringLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();

	private static final List<Goal> GOALS = Arrays.asList(
			Goal.failure(),
			Goal.success(),
			unify(X, lval(1)),
			unify(X, lval(2)),
			Logic.membero(X, LList.ofAll(1, 2, 3)),
			// two derivations of one answer — separates the quotients
			unify(X, lval(1)).or(unify(X, lval(1))),
			Logic.membero(X, LList.ofAll(1, 2, 3)).and(Disequality.separate(X, lval(2))));

	private static List<String> answers(Goal g) {
		return g.solve(X).map(Object::toString).sorted().collect(Collectors.toList());
	}

	/** Same answers with multiplicities — one occurrence per derivation. */
	private static final BiPredicate<Goal, Goal> BY_DERIVATIONS =
			(a, b) -> answers(a).equals(answers(b));

	/** Same answer set — duplicate derivations collapse. */
	private static final BiPredicate<Goal, Goal> BY_ANSWERS =
			(a, b) -> new HashSet<>(answers(a)).equals(new HashSet<>(answers(b)));

	@Test
	public void derivationsFormASemiringUpToAnswerMultisets() {
		SemiringLaws.check(GoalSemirings.DERIVATIONS, GOALS, BY_DERIVATIONS);
	}

	@Test
	public void answersFormAnIdempotentSemiringUpToAnswerSets() {
		SemiringLaws.check(GoalSemirings.ANSWERS, GOALS, BY_ANSWERS);
		IdempotentSemiringLaws.check(GoalSemirings.ANSWERS, GOALS, BY_ANSWERS);
	}

	@Test
	public void theQuotientsGenuinelyDiffer() {
		Goal once = unify(X, lval(1));
		Goal twice = once.or(once);
		assertThat(BY_ANSWERS.test(once, twice)).isTrue();
		assertThat(BY_DERIVATIONS.test(once, twice)).isFalse();
	}
}
