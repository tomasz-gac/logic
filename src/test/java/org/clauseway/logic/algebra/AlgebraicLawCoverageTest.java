package org.clauseway.logic.algebra;

// ABOUTME: The thin coverage gate: implementors of any @CheckedBy algebra in
// ABOUTME: logic must be claimed by a @LawsFor test.

import org.clauseway.functional.laws.LawChecker;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;
import org.clauseway.functional.algebra.CheckedBy;

public class AlgebraicLawCoverageTest {

	@Test
	public void everyAlgebraicInstanceIsClaimedByALawsForTest() throws IOException {
		LawChecker.of(CheckedBy.class).verify(Paths.get("target", "classes"), Paths.get("target", "test-classes"),
				org.junit.AfterClass.class);
	}
}
