package com.tgac.logic.notes;

// ABOUTME: The note store's Domain-cargo negative polarity: notin/exclude carve
// ABOUTME: boxes out of labelled domains via the four moves, without forking.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.notes.Exclusion.exclude;
import static com.tgac.logic.notes.Exclusion.lit;
import static com.tgac.logic.notes.Exclusion.notin;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ExclusionTest {

	@Test
	public void notinCarvesTheBoxOutOfALabelledDomain() {
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 10L))
				.and(notin(x, EnumeratedDomain.range(3L, 6L)));

		List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void notinFailsWhenTheDomainSitsInsideTheBox() {
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(3L, 6L))
				.and(notin(x, EnumeratedDomain.range(0L, 10L)));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void notinIsSatisfiedByADisjointDomain() {
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 3L))
				.and(notin(x, EnumeratedDomain.range(5L, 8L)));

		List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L);
	}

	@Test
	public void excludeEnforcesTheSurvivorWhenAnEscapeDies() {
		// x lands inside its box, so the note's last escape becomes a plain
		// exclusion on y — filtering y's labelling, never forking
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 10L))
				.and(dom(y, EnumeratedDomain.range(0L, 10L)))
				.and(exclude(
						lit(x, EnumeratedDomain.range(2L, 6L)),
						lit(y, EnumeratedDomain.range(5L, 9L))))
				.and(x.unifies(3L));

		List<Long> answers = g.solve(y, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 9L);
	}

	@Test
	public void excludeIsDiscardedWhenAnEscapeComesTrue() {
		// x lands outside its box: the note is satisfied, y stays unfiltered
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 10L))
				.and(dom(y, EnumeratedDomain.range(0L, 10L)))
				.and(exclude(
						lit(x, EnumeratedDomain.range(2L, 6L)),
						lit(y, EnumeratedDomain.range(5L, 9L))))
				.and(x.unifies(0L));

		List<Long> answers = g.solve(y, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}
}
