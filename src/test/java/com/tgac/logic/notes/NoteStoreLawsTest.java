package com.tgac.logic.notes;

// ABOUTME: Lattice laws for the note store: meet is note union, so more
// ABOUTME: notes = lower — claimed for the coverage gate.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
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

	private static final Term<?> X = lvar();
	private static final Term<?> Y = lvar();
	private static final Note X_OUT_OF_LOW = Note.of(List.of(
			Tuple.of(X, EnumeratedDomain.range(0L, 3L))));
	private static final Note Y_OUT_OF_HIGH = Note.of(List.of(
			Tuple.of(Y, EnumeratedDomain.range(5L, 9L))));
	private static final Note EITHER_ESCAPES = Note.of(List.of(
			Tuple.of(X, EnumeratedDomain.range(0L, 3L)),
			Tuple.of(Y, EnumeratedDomain.range(5L, 9L))));

	@Test
	public void noteUnionIsAMeetSemilattice() {
		java.util.List<NoteStore> samples = Arrays.asList(
				NoteStore.of(LinkedHashSet.empty()),
				NoteStore.of(LinkedHashSet.of(X_OUT_OF_LOW)),
				NoteStore.of(LinkedHashSet.of(Y_OUT_OF_HIGH, EITHER_ESCAPES)),
				NoteStore.of(LinkedHashSet.of(X_OUT_OF_LOW, EITHER_ESCAPES)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
