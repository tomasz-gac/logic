package com.tgac.logic.unification;

// ABOUTME: Substitutions form a join-semilattice with join = unification, checked
// ABOUTME: up to solved form — the engine's core state under the same algebra.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import io.vavr.collection.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Substitutions.class)
public class SubstitutionLatticeLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(SubstitutionLatticeLawsTest.class);
	}

	/**
	 * Solved-form equality: over the union of both domains, the deep-walked
	 * value of every variable agrees. {@code {c=a, a=1}} and {@code {c=1, a=1}}
	 * are structurally distinct maps but the same substitution.
	 */
	private static final BiPredicate<Substitutions, Substitutions> BY_SOLVED_FORM = (s1, s2) -> {
		HashSet<LVar<?>> vars = HashSet.ofAll(s1.map().keySet())
				.addAll(s2.map().keySet());
		return vars.forAll(v -> MiniKanren.format(s1, v).equals(MiniKanren.format(s2, v)));
	};

	private static <T> LVar<T> var() {
		return LVar.<T>lvar().asVar().get();
	}

	@Test
	public void substitutionsAreAJoinSemilatticeUpToSolvedForm() {
		LVar<Integer> a = var();
		LVar<Integer> b = var();
		LVar<Integer> c = var();

		List<Substitutions> samples = Arrays.asList(
				Substitutions.empty(),
				Substitutions.empty().extend(a, lval(1)),
				Substitutions.empty().extend(b, lval(2)),
				Substitutions.empty().extend(c, a),                       // c → unbound a
				Substitutions.empty().extend(a, lval(1)).extend(b, lval(2)));

		SemilatticeLaws.checkJoin(samples, BY_SOLVED_FORM);
	}

	@Test
	public void clashingSubstitutionsJoinToTheTopSingletonAbsence() {
		LVar<Integer> a = var();
		Substitutions boundToOne = Substitutions.empty().extend(a, lval(1));
		Substitutions boundToTwo = Substitutions.empty().extend(a, lval(2));

		// no substitution is more specific than both — ⊤, the none singleton
		assertThat(boundToOne.tryJoin(boundToTwo)).isEmpty();
		assertThat(boundToTwo.tryJoin(boundToOne)).isEmpty();

		// compatible joins stay present (⊤ is reached only by a real clash)
		Substitutions boundToOneAgain = Substitutions.empty().extend(a, lval(1));
		assertThat(boundToOne.tryJoin(boundToOneAgain)).isNotEmpty();
	}
}
