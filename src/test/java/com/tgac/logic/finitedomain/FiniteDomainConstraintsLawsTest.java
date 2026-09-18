package com.tgac.logic.finitedomain;

// ABOUTME: Lattice laws for the FD store: pointwise domain meet × propagator-set
// ABOUTME: intersection with a canonical bottom — claimed for the coverage gate.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.finitedomain.capabilities.Arithmetic;
import com.tgac.logic.finitedomain.capabilities.Discrete;
import com.tgac.logic.finitedomain.capabilities.Multiplicative;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.lattice.Verdict;
import com.tgac.logic.lattice.TestPropagators;
import com.tgac.logic.unification.LVar;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor({Leq.class, Lss.class, Add.class, Mul.class, Separate.class})
public class FiniteDomainConstraintsLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(FiniteDomainConstraintsLawsTest.class);
	}

	private static final LVar<?> X = (LVar<?>) lvar().asVar().get();
	private static final LVar<?> Y = (LVar<?>) lvar().asVar().get();
	private static final Propagator KEEP = TestPropagators.of(FiniteDomainConstraints.empty(), "keep",
			Collections.singletonList(X), (watched, state) -> Verdict.keep());

	@Test
	public void schemaAtomsOrderStructurally() {
		// the FD schemas ride the structural default: a schema atom entails
		// exactly itself; distinct schemas and distinct terms are incomparable
		PartialOrderLaws.check(Arrays.asList(
				new Leq(X, Y, Comparator.naturalOrder()),
				new Leq(Y, X, Comparator.naturalOrder()),
				new Lss(X, Y, Comparator.naturalOrder()),
				new Lss(Y, X, Comparator.naturalOrder()),
				new Add(X, Y, X, Arithmetic.LONGS, Comparator.naturalOrder(), Option.of(Discrete.LONGS)),
				new Mul(X, Y, X, Multiplicative.LONGS, Comparator.naturalOrder(), Option.of(Discrete.LONGS)),
				new Separate(X, Y, Comparator.naturalOrder())));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void storeLattice() {
		// the lattice lives on the THEORY; the factor is its execution carrier
		List<Theory<FiniteDomainConstraints>> samples = Arrays.asList(
				Theory.<FiniteDomainConstraints> empty(),
				FiniteDomainConstraints.withDomain(Theory.empty(), X, Longs.interval(0, 10)),
				FiniteDomainConstraints.withDomain(
						FiniteDomainConstraints.withDomain(Theory.empty(), X, Longs.interval(3, 6)),
						Y, Longs.interval(2, 7)),
				FiniteDomainConstraints.withDomain(
						Theory.<FiniteDomainConstraints> empty().with(KEEP),
						Y, Longs.interval(5, 15)));
		SemilatticeLaws.checkLeqReversesAccumulation(samples);
	}
}
