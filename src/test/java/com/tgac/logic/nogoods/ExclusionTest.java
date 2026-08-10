package com.tgac.logic.nogoods;

// ABOUTME: The user front door: exclude states one nogood over literals;
// ABOUTME: FiniteDomain.notin is FD's negated-box sugar over the same door.

import com.tgac.logic.constraints.Statement;
import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.stream.Collectors;
import org.junit.Test;

public class ExclusionTest {

	@Test
	public void excludeVetoesTheForbiddenCombination() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = exclude(Statement.bind(x, lval(3)), Statement.bind(y, lval(4)))
				.and(x.unifies(3))
				.and(y.unifies(4));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void excludeAdmitsEveryEscape() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = exclude(Statement.bind(x, lval(3)), Statement.bind(y, lval(4)))
				.and(x.unifies(3))
				.and(y.unifies(5));

		assertThat(g.solve(y, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void notinCarvesTheBoxOutOfALabelledDomain() {
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 10L))
				.and(FiniteDomain.notin(x, EnumeratedDomain.range(3L, 6L)));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 6L, 7L, 8L, 9L);
	}
}
