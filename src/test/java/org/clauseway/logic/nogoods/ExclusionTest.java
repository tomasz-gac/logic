package org.clauseway.logic.nogoods;

// ABOUTME: The user front door: exclude states one nogood over literals;
// ABOUTME: excluding a dom statement is the negated box — no second door exists.

import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.stream.Collectors;
import org.junit.Test;

public class ExclusionTest {

	@Test
	public void excludeVetoesTheForbiddenCombination() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = exclude(Posting.bind(x, lval(3)), Posting.bind(y, lval(4)))
				.and(x.unifies(3))
				.and(y.unifies(4));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void excludeAdmitsEveryEscape() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = exclude(Posting.bind(x, lval(3)), Posting.bind(y, lval(4)))
				.and(x.unifies(3))
				.and(y.unifies(5));

		assertThat(g.solve(y, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void excludeNegatesUnificationDirectly() {
		// unifies IS a statement, so ¬(x=3 ∧ y=4) needs no bind sugar
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = exclude(x.unifies(3), y.unifies(4))
				.and(x.unifies(3))
				.and(y.unifies(4));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void excludingADomCarvesTheBoxOutOfALabelledDomain() {
		Unifiable<Long> x = lvar();

		Goal g = dom(x, Longs.range(0, 10))
				.and(exclude(dom(x, Longs.range(3, 6))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 6L, 7L, 8L, 9L);
	}
}
