package com.tgac.logic.constraints;

// ABOUTME: Posting is the chokepoint vocabulary lifted to Goal: apply IS the
// ABOUTME: imposition, and Bounded's order is the 0-or-1 taxonomy with the doom bit.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Exhaustion;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class PostingTest {

	@Test
	public void aPostingAppliesAsAPlainGoal() {
		// the lift: the same value FD hands to exclusion is a conjunct
		Unifiable<Long> x = lvar();

		Goal g = FiniteDomain.dom(x, EnumeratedDomain.range(0L, 5L))
				.and(x.unifies(3L));

		List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactly(3L);

		Unifiable<Long> y = lvar();
		Goal outside = FiniteDomain.dom(y, EnumeratedDomain.range(0L, 5L))
				.and(y.unifies(7L));
		assertThat(outside.solve(y, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aPostingPricesAtOne() {
		// blind or sighted with nothing against it: one success, ever
		Unifiable<Long> x = lvar();
		Posting in = FiniteDomain.dom(x, EnumeratedDomain.range(0L, 5L));

		assertThat(in.answers(Package.empty().substitution())).isEqualTo(1L);
		assertThat(in.answers(Package.empty())).isEqualTo(1L);
	}

	@Test
	public void aDoomedPostingPricesAtZero() {
		// the eager 0 under partial knowledge: the live domain is disjoint
		// with the post — failure found at pricing is failure forever
		Unifiable<Long> x = lvar();
		Package live = Exhaustion.collected(
						dom(x, EnumeratedDomain.range(0L, 5L)).apply(Package.empty()))
				.get().get(0);

		Posting doomed = FiniteDomain.dom(x, EnumeratedDomain.range(6L, 9L));
		Posting alive = FiniteDomain.dom(x, EnumeratedDomain.range(3L, 9L));

		assertThat(doomed.answers(live)).isZero();
		assertThat(alive.answers(live)).isEqualTo(1L);
	}
}
