package com.tgac.logic.constraints;

import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
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
		Goal g = dom(x, EnumeratedDomain.range(0L, 3L))
				.and(Disequality.separate(x, lval(1L)));

		List<Long> result = g.solve(x)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(result).containsExactlyInAnyOrder(0L, 2L);
	}

	@Test
	public void disequalityIsEnforcedWhenDeclaredBeforeTheDomain() {
		Unifiable<Long> x = lvar();

		// same query, stores added in the other order
		Goal g = Disequality.separate(x, lval(1L))
				.and(dom(x, EnumeratedDomain.range(0L, 3L)));

		List<Long> result = g.solve(x)
				.map(Term::get)
				.collect(Collectors.toList());

		assertThat(result).containsExactlyInAnyOrder(0L, 2L);
	}
}
