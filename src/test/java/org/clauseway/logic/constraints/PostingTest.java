package org.clauseway.logic.constraints;

// ABOUTME: Posting is the chokepoint vocabulary lifted to Goal: apply IS the
// ABOUTME: imposition, and Bounded's order is a count — doom never prices.

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.FiniteDomain;
import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Exhaustion;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.unification.Term;
import org.clauseway.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class PostingTest {

	@Test
	public void aPostingAppliesAsAPlainGoal() {
		// the lift: the same value FD hands to exclusion is a conjunct
		Unifiable<Long> x = lvar();

		Goal g = FiniteDomain.dom(x, Longs.range(0, 5))
				.and(x.unifies(3L));

		List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactly(3L);

		Unifiable<Long> y = lvar();
		Goal outside = FiniteDomain.dom(y, Longs.range(0, 5))
				.and(y.unifies(7L));
		assertThat(outside.solve(y, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aPostingPricesAtOne() {
		// blind or sighted with nothing against it: one success, ever
		Unifiable<Long> x = lvar();
		Posting in = FiniteDomain.dom(x, Longs.range(0, 5));

		assertThat(in.answers(Package.empty().substitution())).isEqualTo(1L);
		assertThat(in.answers(Package.empty())).isEqualTo(1L);
	}

	@Test
	public void doomNeverPrices() {
		// a count and a verdict are different trust surfaces: the live domain
		// is disjoint with the post — doomed says so, and the price stays 1;
		// the kill is the pruning pass's business (DoomPruner)
		Unifiable<Long> x = lvar();
		Package live = Exhaustion.collected(
						dom(x, Longs.range(0, 5)).apply(Package.empty()))
				.ground().get(0);

		Posting doomed = FiniteDomain.dom(x, Longs.range(6, 9));
		Posting alive = FiniteDomain.dom(x, Longs.range(3, 9));

		assertThat(doomed.doomed(live)).isTrue();
		assertThat(doomed.answers(live)).isEqualTo(1L);
		assertThat(alive.answers(live)).isEqualTo(1L);
	}
}
