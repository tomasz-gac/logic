package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store's receipts: unit propagation imposes the last
// ABOUTME: survivor, entailment discharges, all-refuted fails, leftovers render.

import static com.tgac.logic.disjunction.Disjunction.anyOf;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.TestSchedulers;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class DisjunctionConstraintsTest {

	private static <T> List<T> answers(Goal g, Unifiable<T> out) {
		return g.solve(out, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());
	}

	@Test
	public void unitPropagationImposesTheLastSurvivor() {
		// x = 3 refutes the first alternative at the substitution level;
		// the second is no longer a choice but a consequence — imposed in
		// the same branch, no fork
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Goal g = anyOf(x.unifies(1), y.unifies(5))
				.and(x.unifies(3));

		assertThat(answers(g, y)).containsExactly(5);
	}

	@Test
	public void aStoreVetoedAlternativeStaysOwedAndTheAnswerSaysSo() {
		// the fast path is store-blind by the ruled tradeoff: ¬(x=1) lives
		// in the nogood store, so the binding-shaped disjunct cannot see
		// the veto and stays owed — the answer is CONDITIONAL and carries
		// both residuals, sound and wider; eager cross-store discharge is
		// the doomed-seam's future earliness, not tier one
		Unifiable<Integer> x = lvar();
		Goal g = anyOf(x.unifies(1), x.unifies(2))
				.and(exclude(x.unifies(1)));

		List<String> rendered = g.solve(x, TestSchedulers.factory())
				.map(Object::toString)
				.collect(Collectors.toList());
		assertThat(rendered).hasSize(1);
		assertThat(rendered.get(0)).contains("∨").contains("¬");
	}

	@Test
	public void anEntailedAlternativeDischargesTheDisjunct() {
		// x = 1 satisfies the first alternative: the whole disjunct is
		// spent — y stays free and the answer renders without residual
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Goal g = x.unifies(1)
				.and(anyOf(x.unifies(1), y.unifies(2)));

		List<String> rendered = g.solve(y, TestSchedulers.factory())
				.map(Object::toString)
				.collect(Collectors.toList());
		assertThat(rendered).containsExactly("_.0");
	}

	@Test
	public void allAlternativesRefutedFailsTheBranch() {
		Unifiable<Integer> x = lvar();
		Goal g = x.unifies(3)
				.and(anyOf(x.unifies(1), x.unifies(2)));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aSingleAlternativeIsThePostingItself() {
		// anyOf(a) is a born-unit: the door returns the posting, nothing
		// is ever stored
		Unifiable<Integer> x = lvar();
		assertThat(answers(anyOf(x.unifies(5)), x)).containsExactly(5);
	}

	@Test
	public void anEmptyDisjunctionRefusesLoudly() {
		assertThatThrownBy(Disjunction::anyOf)
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void nestedAnyOfFlattens() {
		// ∨ is associative: anyOf(a, anyOf(b, c)) is one three-way
		// disjunct, so two binding refutations still leave a working unit
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> z = lvar();
		Goal g = anyOf(x.unifies(1), anyOf(y.unifies(2), z.unifies(3)))
				.and(x.unifies(9))
				.and(y.unifies(7));

		assertThat(answers(g, z)).containsExactly(3);
	}

	@Test
	public void anUndecidedDisjunctRendersAsResidual() {
		// no knowledge decides it: the answer is conditional and must say so
		Unifiable<Integer> x = lvar();
		List<String> rendered = anyOf(x.unifies(1), x.unifies(2))
				.solve(x, TestSchedulers.factory())
				.map(Object::toString)
				.collect(Collectors.toList());

		assertThat(rendered).hasSize(1);
		assertThat(rendered.get(0)).contains("∨").contains("≡");
	}

	@Test
	public void fdAlternativesDecideThroughTheStore() {
		// the packaged partition: a domain-shaped alternative born against
		// a contradicting resident domain refutes; the survivor unit-imposes
		Unifiable<Long> x = lvar();
		Goal g = dom(x, EnumeratedDomain.range(0L, 6L))
				.and(anyOf(dom(x, EnumeratedDomain.range(1L, 2L)),
						dom(x, EnumeratedDomain.range(7L, 9L))));

		assertThat(answers(g, x)).containsExactly(1L);
	}
}
