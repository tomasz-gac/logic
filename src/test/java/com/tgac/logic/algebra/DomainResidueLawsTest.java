package com.tgac.logic.algebra;

// ABOUTME: Lattice laws for the FD residue — a positional meet-valued map that
// ABOUTME: is itself a meet-semilattice; absent slot = ⊤.

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.finitedomain.DomainResidue;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import io.vavr.collection.Array;
import io.vavr.collection.HashMap;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(DomainResidue.class)
public class DomainResidueLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(DomainResidueLawsTest.class);
	}

	private static Domain<Long> dom(long... values) {
		Array<Arithmetic<Long>> items = Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted);
		return EnumeratedDomain.of(items);
	}

	@Test
	public void residuesAreAMeetSemilattice() {
		List<DomainResidue> featured = Arrays.asList(
				DomainResidue.of(HashMap.empty()),                          // ⊤ everywhere
				DomainResidue.of(HashMap.of(0, dom(1, 2, 3))),
				DomainResidue.of(HashMap.of(0, dom(2, 3, 5))),
				DomainResidue.of(HashMap.of(1, dom(7))),                    // incomparable with slot-0 ones
				DomainResidue.of(HashMap.of(0, dom(1, 2), 1, dom(7, 8))));
		SemilatticeLaws.checkMeet(featured);
		PartialOrderLaws.check(featured);
	}
}
