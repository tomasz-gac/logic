package org.clauseway.logic.finitedomain;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.constraints.Constraints;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
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

		assertThat(dom(x, Longs.range(1, 11))        // {1..10}
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

		assertThat(dom(x, Longs.range(4, 6))         // {4,5}
				.and(exclude(x.unifies(lval(5L))))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList()))
				.containsExactly(4L);
	}

	@Test(timeout = 5000)
	public void disequalityAgainstTheOnlyCandidateFails() {
		Unifiable<Long> x = lvar();

		long count = dom(x, Longs.range(5, 6))       // {5} exactly
				.and(exclude(x.unifies(lval(5L))))
				.solve(x, TestSchedulers.factory())
				.count();

		assertThat(count).isEqualTo(0);
	}

	@Test(timeout = 5000)
	public void disequalityStatedBeforeTheDomainStaysCorrect() {
		Unifiable<Long> x = lvar();

		assertThat(exclude(x.unifies(lval(5L)))
				.and(dom(x, Longs.range(1, 11)))
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
