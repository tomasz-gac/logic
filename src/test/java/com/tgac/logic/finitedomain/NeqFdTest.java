package com.tgac.logic.finitedomain;

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Disequality and finite domains compose through the substitution alone: the
 * record verifies on every binding, labelling generates candidates, violations
 * die. There is no domain-exclusion bridge (dropped July 2026 — it was
 * optimization-only; constraint-kernel.md/§6): these tests pin
 * that the ANSWER SETS are complete and correct without it, in both statement
 * orders and for both arithmetic and non-arithmetic values.
 */
public class NeqFdTest {

	@Test(timeout = 5000)
	public void groundDisequalityExcludesTheValueFromAnswers() {
		Unifiable<Long> x = lvar();

		assertThat(dom(x, EnumeratedDomain.range(1L, 11L))        // {1..10}
				.and(exclude(x.unifies(lval(5L))))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList()))
				.doesNotContain(5L)
				.hasSize(9);
	}

	@Test(timeout = 5000)
	public void disequalityLeavingOneCandidateYieldsExactlyIt() {
		Unifiable<Long> x = lvar();

		assertThat(dom(x, EnumeratedDomain.range(4L, 6L))         // {4,5}
				.and(exclude(x.unifies(lval(5L))))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList()))
				.containsExactly(4L);
	}

	@Test(timeout = 5000)
	public void disequalityAgainstTheOnlyCandidateFails() {
		Unifiable<Long> x = lvar();

		long count = dom(x, EnumeratedDomain.range(5L, 6L))       // {5} exactly
				.and(exclude(x.unifies(lval(5L))))
				.solve(x, TestSchedulers.factory())
				.count();

		assertThat(count).isEqualTo(0);
	}

	@Test(timeout = 5000)
	public void disequalityStatedBeforeTheDomainStaysCorrect() {
		Unifiable<Long> x = lvar();

		assertThat(exclude(x.unifies(lval(5L)))
				.and(dom(x, EnumeratedDomain.range(1L, 11L)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList()))
				.doesNotContain(5L)
				.hasSize(9);
	}

	@Test(timeout = 5000)
	public void nonArithmeticDisequalityKeepsItsRecord() {
		Unifiable<String> s = lvar();

		assertThat(exclude(s.unifies(lval("no")))
				.and(Constraints.unify(s, lval("yes")))
				.solve(s, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList()))
				.containsExactly("yes");
	}
}
