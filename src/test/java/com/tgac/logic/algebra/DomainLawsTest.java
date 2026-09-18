package com.tgac.logic.algebra;

// ABOUTME: Lattice laws for every Domain implementation, each featured by its
// ABOUTME: own samples — claimed via @LawsFor for the coverage gate.

import com.tgac.functional.algebra.laws.AbsorbingLaws;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.Longs;
import com.tgac.logic.finitedomain.domains.Empty;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.finitedomain.domains.Union;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Test;

@LawsFor({EnumeratedDomain.class, Interval.class, Singleton.class, Union.class, Empty.class})
public class DomainLawsTest {

	@AfterClass
	public static void lawClaimsExercised() {
		LawCoverage.verifyClaimsExercised(DomainLawsTest.class);
	}

	private static void laws(List<Domain<Long>> featured) {
		SemilatticeLaws.checkLeqReversesAccumulation(featured);
		AbsorbingLaws.check(featured);
	}

	@Test
	public void enumerated() {
		laws(Arrays.asList(
				Longs.enumerated(2L, 3L, 5L, 8L),
				Longs.enumerated(3L, 5L, 9L),
				Longs.enumerated(1L, 7L),
				Empty.instance()));
	}

	@Test
	public void intervals() {
		laws(Arrays.asList(
				Longs.interval(0, 10),
				Longs.interval(3, 6),
				Longs.interval(8, 15),
				Empty.instance()));
	}

	@Test
	public void singletons() {
		laws(Arrays.asList(
				Longs.singleton(5),
				Longs.singleton(9),
				Longs.interval(3, 6),
				Empty.instance()));
	}

	@Test
	public void unions() {
		laws(Arrays.asList(
				Longs.interval(0, 15).difference(Longs.interval(5, 9)),
				Longs.interval(2, 12).difference(Longs.interval(6, 7)),
				Longs.interval(4, 11),
				Empty.instance()));
	}

	@Test
	public void empty() {
		laws(Arrays.asList(
				Empty.instance(),
				Longs.interval(0, 4),
				Longs.enumerated(2L, 3L)));
	}
}
