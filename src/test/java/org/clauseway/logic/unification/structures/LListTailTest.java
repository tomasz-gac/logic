package org.clauseway.logic.unification.structures;

// ABOUTME: Pins the list-reading face: elements() is the proper prefix,
// ABOUTME: openTail() the dangling hole of an improper list.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.clauseway.logic.unification.terms.LVar;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.junit.Test;

public class LListTailTest {

	@Test
	public void elementsListTheProperPrefixInOrder() {
		LList<Integer> list = LList.ofAll(1, 2, 3).get();
		assertThat(list.elements().map(Term::get).collect(Collectors.toList()))
				.containsExactly(1, 2, 3);
	}

	@Test
	public void aProperListHasNoOpenTail() {
		assertThat(LList.ofAll(1, 2).get().openTail()).isEmpty();
	}

	@Test
	public void anImproperListStopsAtTheHoleAndReportsIt() {
		Unifiable<Integer> head = lval(1);
		Unifiable<LList<Integer>> hole = LVar.lvar();
		LList<Integer> improper = LList.of(head, hole).get();
		assertThat(improper.elements().map(Term::get).collect(Collectors.toList()))
				.containsExactly(1);
		assertThat(improper.openTail()).contains(hole);
	}

	@Test
	public void theEmptyListHasNoElementsAndNoOpenTail() {
		assertThat(LList.<Integer> empty().get().elements()).isEmpty();
		assertThat(LList.<Integer> empty().get().openTail()).isEmpty();
	}
}
