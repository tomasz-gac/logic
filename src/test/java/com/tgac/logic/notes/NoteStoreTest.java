package com.tgac.logic.notes;

// ABOUTME: The store faces over the verification core: the four moves through the
// ABOUTME: real propagation pipeline — statement, revise on bindings, the wall.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.tgac.logic.finitedomain.FiniteDomain.dom;

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.unification.Term;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import java.util.stream.Collectors;
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
		// y = 2 crosses its posting off; the survivor is ¬(x = 1). The
		// intermediate assertion proves the branch is alive after the
		// cross-off, so the zero can only come from the survivor's veto
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		Goal afterCrossOff = held(Posting.bind(x, lval(1)), Posting.bind(y, lval(2)))
				.and(y.unifies(2));
		assertThat(afterCrossOff.solve(y, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(2);

		assertThat(afterCrossOff.and(x.unifies(1))
				.solve(x, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void theSurvivorAdmitsTheEscape() {
		// the escape path of the same pair as above
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
		// after the statement there is no further trigger, so the zero can
		// only come from first examination — the wall cannot produce it
		// (x is ground: no live name renders, so reify stays silent). The
		// sibling statement proves first examination discriminates: a note
		// born SATISFIED discards and the branch delivers
		Unifiable<Integer> x = lvar();

		Goal violated = x.unifies(3).and(held(Posting.bind(x, lval(3))));
		assertThat(violated.solve(x, TestSchedulers.factory()).count()).isZero();

		Unifiable<Integer> z = lvar();
		Goal discarded = z.unifies(3).and(held(Posting.bind(z, lval(4))));
		assertThat(discarded.solve(z, TestSchedulers.factory()).findFirst().get().get())
				.isEqualTo(3);
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

	@Test
	public void aBindNoteFiltersAtLabellingOnly() {
		// the note stays undecided until enforce-time labelling
		// equality-binds the anchor — the ground floor through the binding
		// seam, per labelled point
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 5L))
				.and(held(Posting.bind(x, lval(3L))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 4L);
	}

	@Test
	public void aDisjointDomainDischargesTheNoteThroughTheStore() {
		// the constraint-failing path: the imposition fails via the FD meet
		// emptying in the scratch, not via unification — refuted, discarded
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 4L))
				.and(held(FiniteDomain.in(x, EnumeratedDomain.range(5L, 8L))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 3L);
	}

	@Test
	public void theNoteCarvesTheBoxOutOfALabelledDomain() {
		// pre-labelling the note narrows nothing (the imposition would narrow
		// the scratch, so the posting stays owed); each labelled point inside
		// the box reads entailed at the ground floor and dies
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(0L, 10L))
				.and(held(FiniteDomain.in(x, EnumeratedDomain.range(3L, 6L))));

		java.util.List<Long> answers = g.solve(x, TestSchedulers.factory())
				.map(Term::get).collect(Collectors.toList());
		assertThat(answers).containsExactlyInAnyOrder(0L, 1L, 2L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void aDomainInsideTheBoxDiesAtStatement() {
		// the resident domain sits inside the forbidden box: the imposition
		// meets to no change, the single posting reads entailed — the veto,
		// through the store's own factor rather than a binding
		Unifiable<Long> x = lvar();

		Goal g = dom(x, EnumeratedDomain.range(3L, 6L))
				.and(held(FiniteDomain.in(x, EnumeratedDomain.range(0L, 10L))));

		assertThat(g.solve(x, TestSchedulers.factory()).count()).isZero();
	}
}
