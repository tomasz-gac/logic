package org.clauseway.logic.finitedomain.domains;

import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.Ints;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class IntervalTest {
	@Test
	public void shouldSplitIntervalWithSingleton() {
		Domain<Integer> domain1 = Ints.interval(0, 10);
		Domain<Integer> domain2 = Ints.singleton(5);

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Union.of(
						Ints.interval(0, 4),
						Ints.interval(6, 10)));
	}

	@Test
	public void shouldSplitIntervalWithSingletonDisjoint() {
		Domain<Integer> domain1 = Ints.interval(0, 10);
		Domain<Integer> domain2 = Ints.singleton(11);

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Ints.interval(0, 10));
	}

	@Test
	public void shouldDiffMultiInterval() {
		Domain<Integer> domain1 = Ints.interval(0, 10);
		Union<Integer> domain2 = Union.of(
				Ints.interval(0, 4), Ints.interval(6, 10));

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Ints.singleton(5));
	}

	@Test
	public void shouldIntersectDomains() {
		Domain<Integer> domain1 = Ints.interval(0, 5);
		Domain<Integer> domain2 = Ints.interval(5, 10);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Ints.singleton(5));
	}

	@Test
	public void shouldIntersectDomains2() {
		Domain<Integer> domain1 = Ints.interval(0, 7);
		Domain<Integer> domain2 = Ints.interval(3, 10);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Ints.interval(3, 7));
	}

	@Test
	public void shouldIntersectDomains3() {
		Domain<Integer> domain1 = Ints.interval(0, 7);
		Domain<Integer> domain2 = Ints.interval(10, 11);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffEqual() {
		Assertions.assertThat(
						Ints.interval(0, 10).difference(Ints.interval(0, 10)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffContaining() {
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Ints.interval(-10, 20)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffRight() {
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Ints.interval(5, 20)))
				.isEqualTo(Ints.interval(0, 4));
	}

	@Test
	public void shouldDiffLeft() {
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Ints.interval(-10, 5)))
				.isEqualTo(Ints.interval(6, 10));
	}

	@Test
	public void shouldDiffContained() {
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Ints.interval(3, 6)))
				.isEqualTo(Union.of(
						Ints.interval(0, 2),
						Ints.interval(7, 10)));
	}

	@Test
	public void shouldDiffDisjoint() {
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Ints.interval(11, 20)))
				.isEqualTo(Ints.interval(0, 10));
	}

	@Test
	public void shouldDiffEmpty() {
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Empty.instance()))
				.isEqualTo(Ints.interval(0, 10));
	}

	@Test
	public void shouldStream() {
		Assertions.assertThat(Ints.interval(0, 3).stream())
				.containsExactly(0, 1, 2, 3);
	}

	@Test
	public void shouldAtLeast() {
		Assertions.assertThat(Ints.interval(0, 3).atLeast(2))
				.isEqualTo(Ints.interval(2, 3));
	}

	@Test
	public void shouldAtLeast1() {
		Assertions.assertThat(Ints.interval(0, 3).atLeast(0))
				.isEqualTo(Ints.interval(0, 3));
	}

	@Test
	public void shouldAtLeast3() {
		Assertions.assertThat(Ints.interval(0, 3).atLeast(3))
				.isEqualTo(Ints.singleton(3));
	}

	@Test
	public void shouldAtLeast4() {
		Assertions.assertThat(Ints.interval(0, 3).atLeast(5))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldAtLeast5() {
		Assertions.assertThat(Ints.interval(0, 3).atLeast(-2))
				.isEqualTo(Ints.interval(0, 3));
	}

	@Test
	public void shouldAtMost() {
		Assertions.assertThat(Ints.interval(0, 3).atMost(2))
				.isEqualTo(Ints.interval(0, 2));
	}

	@Test
	public void shouldAtMost2() {
		Assertions.assertThat(Ints.interval(0, 3).atMost(0))
				.isEqualTo(Ints.singleton(0));
	}

	@Test
	public void shouldAtMost3() {
		Assertions.assertThat(Ints.interval(0, 3).atMost(3))
				.isEqualTo(Ints.interval(0, 3));
	}

	@Test
	public void shouldAtMost4() {
		Assertions.assertThat(Ints.interval(0, 3).atMost(5))
				.isEqualTo(Ints.interval(0, 3));
	}

	@Test
	public void shouldDifferenceEnumerated() {
		Domain<Integer> expected = Union.of(
				Ints.singleton(0),
				Ints.singleton(2),
				Ints.singleton(4),
				Ints.interval(6, 10));
		Assertions.assertThat(
						Ints.interval(0, 10)
								.difference(Ints.enumerated(1, 3, 5)))
				.isEqualTo(
						expected);
	}

	@Test
	public void shouldTestDisjointEmpty() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Empty.instance()))
				.isTrue();
	}

	@Test
	public void shouldTestDisjointSingle() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.singleton(3)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSingle2() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.singleton(30)))
				.isTrue();
	}

	@Test
	public void shouldTestDisjointSimple() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.interval(-3, 1)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSimple2() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.interval(9, 11)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSimple3() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.interval(11, 30)))
				.isTrue();
	}

	@Test
	public void shouldTestMulti() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Union.of(
								Ints.interval(-10, -1),
								Ints.interval(11, 30)
						)))
				.isTrue();
	}

	@Test
	public void shouldTestMulti2() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Union.of(
								Ints.interval(-10, 2),
								Ints.interval(11, 30)
						)))
				.isFalse();
	}

	@Test
	public void shouldTestEnumerated() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.enumerated(1, 11, 12)))
				.isFalse();
	}

	@Test
	public void shouldTestEnumerated2() {
		Assertions.assertThat(Ints.interval(0, 10)
						.isDisjoint(Ints.enumerated(-1, 11, 12)))
				.isTrue();
	}

	@Test
	public void shouldDiffWithSingleton() {
		Assertions.assertThat(
						Ints.interval(9, 19).difference(Ints.singleton(9)))
				.isEqualTo(Ints.interval(10, 19));
	}

}