package com.tgac.logic.lattice;

// ABOUTME: Laws for Imposition — the atom leq sharp over same-target domains,
// ABOUTME: and the declared Semilattice: same-target combine is the domain meet.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.AbsorbingLaws;
import com.tgac.functional.algebra.laws.PartialOrderLaws;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Unifiable;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor(Imposition.class)
public class ImpositionLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(ImpositionLawsTest.class);
	}

	private static final Unifiable<Integer> X = lvar();
	private static final Unifiable<Integer> Y = lvar();

	private static Imposition<FlatSet, FlatConstraints> on(Unifiable<Integer> target, Object... values) {
		return new Imposition<>(FlatConstraints.class, target, FlatSet.of(values), FlatConstraints.empty());
	}

	@Test
	public void domainOrderIsAPartialOrder() {
		java.util.List<Atom<FlatConstraints>> samples = Arrays.asList(
				on(X, 1),
				on(X, 1, 2),
				on(X, 1, 2, 3),
				on(X, 2),
				on(Y, 1, 2));
		PartialOrderLaws.check(samples);
	}

	@Test
	public void sameTargetImpositionsFormAMeetSemilattice() {
		java.util.List<Imposition<FlatSet, FlatConstraints>> samples = Arrays.asList(
				on(X, 1),
				on(X, 1, 2),
				on(X, 2, 3),
				on(X, 1, 2, 3));
		SemilatticeLaws.check(samples);
	}

	@Test
	public void theEmptyDomainImpositionIsTheAbsorber() {
		// x ⊂ ∅ absorbs every same-slot meet and is terminal — refutational
		// knowledge as a lawful plan value
		java.util.List<Imposition<FlatSet, FlatConstraints>> samples = Arrays.asList(
				on(X),
				on(X, 1),
				on(X, 1, 2),
				on(X, 1, 2, 3));
		AbsorbingLaws.check(samples);
	}

	@Test
	public void theAtomLeqIsSharpOverSameTargetDomains() {
		assertThat(on(X, 2).leq(on(X, 1, 2))).isTrue();
		assertThat(on(X, 1, 2).leq(on(X, 2))).isFalse();
	}

	@Test
	public void differentTargetsAreIncomparable() {
		assertThat(on(X, 1, 2).leq(on(Y, 1, 2))).isFalse();
		assertThat(on(Y, 1, 2).leq(on(X, 1, 2))).isFalse();
	}

	@Test
	public void combineIsConsistentWithTheAtomLeq() {
		Imposition<FlatSet, FlatConstraints> met = on(X, 1, 2).combine(on(X, 2, 3));
		assertThat(met).isEqualTo(on(X, 2));
		assertThat(met.leq(on(X, 1, 2))).isTrue();
		assertThat(met.leq(on(X, 2, 3))).isTrue();
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void impositionsOfDifferentFamiliesNeverDominate() {
		// same target, same domain type, DIFFERENT lattice family: without the
		// family in the slot condition these would dominate each other
		Unifiable<Integer> x = lvar();
		Imposition mine = new Imposition<>(FlatConstraints.class, x, FlatSet.of(1), FlatConstraints.empty());
		Imposition other = new Imposition(OtherFamily.class, x, FlatSet.of(1, 2), null);
		assertThat(mine.leq((Imposition<FlatSet, FlatConstraints>) other)).isFalse();
		assertThat(other.leq(mine)).isFalse();
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void combineRefusesDifferentFamiliesLoudly() {
		Unifiable<Integer> x = lvar();
		Imposition mine = new Imposition<>(FlatConstraints.class, x, FlatSet.of(1), FlatConstraints.empty());
		Imposition other = new Imposition(OtherFamily.class, x, FlatSet.of(1, 2), null);
		assertThatThrownBy(() -> mine.combine(other))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** A second family token; never instantiated — the class is the point. */
	private static abstract class OtherFamily extends LatticeFactor<FlatSet, OtherFamily> {
		private OtherFamily() {
		}
	}

	@Test
	public void combineRefusesDifferentTargetsLoudly() {
		// unreachable through Theory's collision guard; loud if called directly
		assertThatThrownBy(() -> on(X, 1, 2).combine(on(Y, 2, 3)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
