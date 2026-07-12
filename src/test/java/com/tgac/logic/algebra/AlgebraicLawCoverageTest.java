package com.tgac.logic.algebra;

// ABOUTME: The thin coverage gate: implementors of any @CheckedBy algebra in
// ABOUTME: logic must be claimed by a @LawsFor test.

import com.tgac.functional.algebra.laws.LawCoverage;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;

public class AlgebraicLawCoverageTest {

	@Test
	public void everyAlgebraicInstanceIsClaimedByALawsForTest() throws IOException {
		LawCoverage.verify(Paths.get("target", "classes"), Paths.get("target", "test-classes"));
	}
}
