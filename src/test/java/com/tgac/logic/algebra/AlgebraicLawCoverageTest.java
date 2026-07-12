package com.tgac.logic.algebra;

// ABOUTME: The thin coverage gate: every algebraic implementor in logic must be
// ABOUTME: claimed by a @LawsFor-annotated test class.

import com.tgac.functional.algebra.Bottomed;
import com.tgac.functional.algebra.CommutativeMonoid;
import com.tgac.functional.algebra.JoinSemilattice;
import com.tgac.functional.algebra.Lattice;
import com.tgac.functional.algebra.MeetSemilattice;
import com.tgac.functional.algebra.Monoid;
import com.tgac.functional.algebra.Semiring;
import com.tgac.functional.algebra.laws.LawCoverage;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;

public class AlgebraicLawCoverageTest {

	@Test
	public void everyAlgebraicInstanceIsClaimedByALawsForTest() throws IOException {
		LawCoverage.verify(
				Paths.get("target", "classes"),
				Paths.get("target", "test-classes"),
				MeetSemilattice.class, JoinSemilattice.class, Lattice.class,
				Monoid.class, CommutativeMonoid.class, Semiring.class, Bottomed.class);
	}
}
