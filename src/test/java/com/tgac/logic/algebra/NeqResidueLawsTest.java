package com.tgac.logic.algebra;

// ABOUTME: Order laws for the Neq residue — record-set containment as entailment
// ABOUTME: over transcribed disequalities; claimed for the coverage gate.

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.logic.separate.NeqResidue;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(NeqResidue.class)
public class NeqResidueLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(NeqResidueLawsTest.class);
	}

	private static HashMap<Integer, Term<?>> record(int slot, Term<?> forbidden) {
		return HashMap.of(slot, forbidden);
	}

	@Test
	public void residuesArePartiallyOrderedByRecordContainment() {
		List<NeqResidue> featured = Arrays.asList(
				NeqResidue.of(HashSet.empty()),                                  // ⊤: no disequalities
				NeqResidue.of(HashSet.of(record(0, lval(5)))),
				NeqResidue.of(HashSet.of(record(0, lval(5)), record(0, lval(6)))),
				NeqResidue.of(HashSet.of(record(1, lval(7)))),                   // incomparable with slot-0 ones
				NeqResidue.of(HashSet.of(record(0, Hole.of(1)))));               // a coupled record
		PartialOrderLaws.check(featured);
	}
}
