package com.tgac.logic.finitedomain;

// ABOUTME: Lattice laws for the FD store: pointwise domain meet × propagator-set
// ABOUTME: intersection with a canonical bottom — claimed for the coverage gate.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.BottomedLaws;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.unification.LVar;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(FiniteDomainConstraints.class)
public class FiniteDomainConstraintsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(FiniteDomainConstraintsLawsTest.class);
	}

	private static final LVar<?> X = (LVar<?>) lvar().asVar().get();
	private static final LVar<?> Y = (LVar<?>) lvar().asVar().get();
	private static final Propagator KEEP = Propagator.of(FiniteDomainConstraints.class,
			Collections.singletonList(X), state -> Verdict.keep());

	@Test
	public void storeLattice() {
		List<FiniteDomainConstraints> samples = Arrays.asList(
				FiniteDomainConstraints.empty(),
				FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(0L, 10L)),
				FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(3L, 6L))
						.withDomain(Y, Interval.of(2L, 7L)),
				((FiniteDomainConstraints) FiniteDomainConstraints.empty().prepend(KEEP))
						.withDomain(Y, Interval.of(5L, 15L)),
				FiniteDomainConstraints.bottom());
		SemilatticeLaws.checkMeet(samples);
		BottomedLaws.check(samples);
	}
}
