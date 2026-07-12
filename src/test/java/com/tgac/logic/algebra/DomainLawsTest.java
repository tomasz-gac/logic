package com.tgac.logic.algebra;

// ABOUTME: Lattice laws for every Domain implementation, each featured by its
// ABOUTME: own samples — claimed via @LawsFor for the coverage gate.

import com.tgac.functional.algebra.laws.BottomedLaws;
import com.tgac.functional.algebra.laws.LawCoverage;
import com.tgac.functional.algebra.laws.LawsFor;
import com.tgac.functional.algebra.laws.SemilatticeLaws;
import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.Empty;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.finitedomain.domains.Union;
import io.vavr.collection.Array;
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
		SemilatticeLaws.checkMeet(featured);
		BottomedLaws.check(featured);
	}

	@Test
	public void enumerated() {
		laws(Arrays.asList(
				EnumeratedDomain.of(Array.of(2L, 3L, 5L, 8L).map(Arithmetic::ofCasted)),
				EnumeratedDomain.of(Array.of(3L, 5L, 9L).map(Arithmetic::ofCasted)),
				EnumeratedDomain.of(Array.of(1L, 7L).map(Arithmetic::ofCasted)),
				Empty.instance()));
	}

	@Test
	public void intervals() {
		laws(Arrays.asList(
				Interval.of(0L, 10L),
				Interval.of(3L, 6L),
				Interval.of(8L, 15L),
				Empty.instance()));
	}

	@Test
	public void singletons() {
		laws(Arrays.asList(
				Singleton.of(Arithmetic.ofCasted(5L)),
				Singleton.of(Arithmetic.ofCasted(9L)),
				Interval.of(3L, 6L),
				Empty.instance()));
	}

	@Test
	public void unions() {
		laws(Arrays.asList(
				Interval.of(0L, 15L).difference(Interval.of(5L, 9L)),
				Interval.of(2L, 12L).difference(Interval.of(6L, 7L)),
				Interval.of(4L, 11L),
				Empty.instance()));
	}

	@Test
	public void empty() {
		laws(Arrays.asList(
				Empty.instance(),
				Interval.of(0L, 4L),
				EnumeratedDomain.of(Array.of(2L, 3L).map(Arithmetic::ofCasted))));
	}
}
