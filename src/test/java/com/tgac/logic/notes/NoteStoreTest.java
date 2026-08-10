package com.tgac.logic.notes;

// ABOUTME: The store faces over the verification core: the four moves through the
// ABOUTME: real propagation pipeline — statement, revise on bindings, the wall.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import org.junit.Test;

public class NoteStoreTest {

	private static Goal held(Posting... postings) {
		Note note = Note.of(List.of(postings));
		return pkg -> Propagation.activate(note).apply(NoteStore.register(pkg));
	}

	@Test
	public void aViolatedNoteFailsTheBranch() {
		Unifiable<Integer> x = lvar();

		Goal g = held(Posting.bind(x, lval(3))).and(x.unifies(3));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aSatisfiedNoteDischarges() {
		Unifiable<Integer> x = lvar();

		Goal g = held(Posting.bind(x, lval(3))).and(x.unifies(5));

		assertThat(g.solve(x, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void theSurvivorVetoesAfterACrossOff() {
		// y = 2 crosses its posting off; the survivor is ¬(x = 1)
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)))
				.and(y.unifies(2))
				.and(x.unifies(1));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void theSurvivorAdmitsTheEscape() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)))
				.and(y.unifies(2))
				.and(x.unifies(5));

		assertThat(g.solve(x, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(5);
	}

	@Test
	public void aNoteBornViolatedFailsAtStatement() {
		Unifiable<Integer> x = lvar();

		Goal g = x.unifies(3).and(held(Posting.bind(x, lval(3))));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aLiveNoteAboutARenderedTermRefusesToRenderSilently() {
		// both postings stay owed; the answer would carry the note's condition
		// invisibly — the stage wall refuses instead
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal g = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)));

		assertThatThrownBy(() -> g.solve(x, TestSchedulers.factory()).count())
				.isInstanceOf(IllegalStateException.class);
	}
}
