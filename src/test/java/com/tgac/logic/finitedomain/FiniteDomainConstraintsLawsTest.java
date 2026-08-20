package com.tgac.logic.finitedomain;

// ABOUTME: Lattice laws for the FD store: pointwise domain meet × propagator-set
// ABOUTME: intersection with a canonical bottom — claimed for the coverage gate.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.functional.algebra.laws.AbsorbingLaws;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.unification.LVar;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor({FiniteDomainConstraints.class, LeqO.class, AddO.class, MulO.class, SeparateO.class})
public class FiniteDomainConstraintsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(FiniteDomainConstraintsLawsTest.class);
	}

	private static final LVar<?> X = (LVar<?>) lvar().asVar().get();
	private static final LVar<?> Y = (LVar<?>) lvar().asVar().get();
	private static final Propagator KEEP = Propagator.of(FiniteDomainConstraints.empty(), "keep",
			Collections.singletonList(X), (watched, state) -> Verdict.keep());

	@Test
	public void schemaAtomsOrderStructurally() {
		// the FD schemas ride the structural default: a schema atom entails
		// exactly itself; distinct schemas and distinct terms are incomparable
		PartialOrderLaws.check(Arrays.asList(
				new LeqO(X, Y),
				new LeqO(Y, X),
				new AddO(X, Y, X),
				new MulO(X, Y, X),
				new SeparateO(X, Y)));
	}

	@Test
	public void storeLattice() {
		// the lattice lives on the THEORY; the factor is its execution carrier
		List<Theory<FiniteDomainConstraints>> samples = Arrays.asList(
				FiniteDomainConstraints.empty().theory(),
				FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(0L, 10L)).theory(),
				FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(3L, 6L))
						.withDomain(Y, Interval.of(2L, 7L)).theory(),
				((FiniteDomainConstraints) FiniteDomainConstraints.empty().meet(KEEP))
						.withDomain(Y, Interval.of(5L, 15L)).theory());
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}

	@Test
	public void theFamilyInternalFactorSemilattice() {
		// LatticeFactor declares Semilattice & Absorbing family-internally —
		// its cascade's termination theorem needs the premise; combine is
		// absorb with the terminal-bottom guard on both sides
		List<FiniteDomainConstraints> samples = Arrays.asList(
				FiniteDomainConstraints.empty(),
				FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(0L, 10L)),
				FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(3L, 6L))
						.withDomain(Y, Interval.of(2L, 7L)),
				FiniteDomainConstraints.bottom());
		SemilatticeLaws.check(samples);
		AbsorbingLaws.check(samples);
	}

	@Test
	public void bottomIsAbsorbingAtTheExecutionFace() {
		// with factor meets gone, the absorber's law surface is absorb:
		// bottom refuses knowledge, and a theory carrying bottom lands there
		org.junit.Assert.assertTrue(FiniteDomainConstraints.bottom().isAbsorbing());
		org.junit.Assert.assertTrue(FiniteDomainConstraints.bottom()
				.absorb(FiniteDomainConstraints.empty()
						.withDomain(X, Interval.of(0L, 10L)).theory())
				.isAbsorbing());
	}
}
