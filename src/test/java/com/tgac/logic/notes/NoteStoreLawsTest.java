package com.tgac.logic.notes;

// ABOUTME: Lattice laws for the note store: meet is note union, so more
// ABOUTME: notes = lower — claimed for the coverage gate.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(NoteStore.class)
public class NoteStoreLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(NoteStoreLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();
	private static final Note X_APART = Note.of(List.of(Posting.bind(X, lval(1))));
	private static final Note Y_APART = Note.of(List.of(Posting.bind(Y, lval(2))));
	private static final Note NOT_BOTH = Note.of(List.of(
			Posting.bind(X, lval(1)), Posting.bind(Y, lval(2))));

	@Test
	public void noteUnionIsAMeetSemilattice() {
		java.util.List<NoteStore> samples = Arrays.asList(
				NoteStore.of(LinkedHashSet.empty()),
				NoteStore.of(LinkedHashSet.of(X_APART)),
				NoteStore.of(LinkedHashSet.of(Y_APART, NOT_BOTH)),
				NoteStore.of(LinkedHashSet.of(X_APART, NOT_BOTH)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
