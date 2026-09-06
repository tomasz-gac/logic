package com.tgac.logic.nogoods;

// ABOUTME: exclude accepts any Postable — foreign literals convert at the door,
// ABOUTME: Postings pass through as themselves, mixes form one forbidden conjunction.

import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Postable;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class PostableTest {

	private static List<String> answers(Goal g, Unifiable<?> out) {
		return g.solve(out).map(Object::toString).sorted().collect(Collectors.toList());
	}

	@Test
	public void aForeignPostableConvertsAtTheDoor() {
		Unifiable<Integer> x = lvar();
		Postable lit = () -> x.unifies(3);
		assertThat(answers(exclude(lit).and(x.unifies(3)), x)).isEmpty();

		Unifiable<Integer> y = lvar();
		Postable lit2 = () -> y.unifies(3);
		assertThat(answers(exclude(lit2).and(y.unifies(5)), y)).containsExactly("{5}");
	}

	@Test
	public void mixedLiteralsFormOneForbiddenConjunction() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Postable lit = () -> x.unifies(3);
		assertThat(answers(exclude(lit, y.unifies(4))
				.and(x.unifies(3)).and(y.unifies(4)), x)).isEmpty();

		Unifiable<Integer> x2 = lvar();
		Unifiable<Integer> y2 = lvar();
		Postable lit2 = () -> x2.unifies(3);
		assertThat(answers(exclude(lit2, y2.unifies(4))
				.and(x2.unifies(3)).and(y2.unifies(5)), x2)).containsExactly("{3}");
	}

	@Test
	public void aPostingIsItsOwnPostable() {
		Unifiable<Integer> x = lvar();
		Postable asPostable = x.unifies(3);
		assertThat(asPostable.posted()).isSameAs(asPostable);
	}
}
