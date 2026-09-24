package org.clauseway.logic.constraints;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.Longs;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * A query that combines a disequality (Neq) constraint with a finite-domain
 * constraint. Both stores must enforce their constraints as the domain is
 * labelled; neither may starve the other of the substitution prefix.
 */
public class NeqFiniteDomainTest {

	@Test
	public void disequalityIsEnforcedAlongsideFiniteDomain() {
		Unifiable<Long> x = lvar();

		// x in {0, 1, 2} and x != 1  ->  {0, 2}
		Goal g = dom(x, Longs.range(0, 3))
				.and(exclude(x.unifies(lval(1L))));

		List<Long> result = g.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(result).containsExactlyInAnyOrder(0L, 2L);
	}

	@Test
	public void disequalityIsEnforcedWhenDeclaredBeforeTheDomain() {
		Unifiable<Long> x = lvar();

		// same query, stores added in the other order
		Goal g = exclude(x.unifies(lval(1L)))
				.and(dom(x, Longs.range(0, 3)));

		List<Long> result = g.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(result).containsExactlyInAnyOrder(0L, 2L);
	}
}
