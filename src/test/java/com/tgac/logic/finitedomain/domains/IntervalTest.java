package com.tgac.logic.finitedomain.domains;
import com.tgac.logic.finitedomain.Domain;
import io.vavr.collection.Array;
import org.assertj.core.api.Assertions;
import org.junit.Test;
public class IntervalTest {
	@Test
	public void shouldSplitIntervalWithSingleton() {
		Interval<Integer> domain1 = Interval.of(0, 10);
		Singleton<Integer> domain2 = Singleton.of(5);

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Union.of(
						Interval.of(0, 4),
						Interval.of(6, 10)));
	}
	@Test
	public void shouldSplitIntervalWithSingletonDisjoint() {
		Interval<Integer> domain1 = Interval.of(0, 10);
		Singleton<Integer> domain2 = Singleton.of(11);

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Interval.of(0, 10));
	}

	@Test
	public void shouldDiffMultiInterval() {
		Interval<Integer> domain1 = Interval.of(0, 10);
		Union<Integer> domain2 = Union.of(
				Interval.of(0, 4), Interval.of(6, 10));

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Singleton.of(5));
	}

	@Test
	public void shouldIntersectDomains() {
		Interval<Integer> domain1 = Interval.of(0, 5);
		Interval<Integer> domain2 = Interval.of(5, 10);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Singleton.of(5));
	}

	@Test
	public void shouldIntersectDomains2() {
		Interval<Integer> domain1 = Interval.of(0, 7);
		Interval<Integer> domain2 = Interval.of(3, 10);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Interval.of(3, 7));
	}

	@Test
	public void shouldIntersectDomains3() {
		Interval<Integer> domain1 = Interval.of(0, 7);
		Interval<Integer> domain2 = Interval.of(10, 11);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffEqual() {
		Assertions.assertThat(
						Interval.of(0, 10).difference(Interval.of(0, 10)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffContaining() {
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(Interval.of(-10, 20)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffRight() {
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(Interval.of(5, 20)))
				.isEqualTo(Interval.of(0, 4));
	}

	@Test
	public void shouldDiffLeft() {
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(Interval.of(-10, 5)))
				.isEqualTo(Interval.of(6, 10));
	}

	@Test
	public void shouldDiffContained() {
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(Interval.of(3, 6)))
				.isEqualTo(Union.of(
						Interval.of(0, 2),
						Interval.of(7, 10)));
	}

	@Test
	public void shouldDiffDisjoint() {
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(Interval.of(11, 20)))
				.isEqualTo(Interval.of(0, 10));
	}

	@Test
	public void shouldDiffEmpty() {
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(Empty.instance()))
				.isEqualTo(Interval.of(0, 10));
	}

	@Test
	public void shouldStream() {
		Assertions.assertThat(Interval.of(0, 3).stream())
				.containsExactly(0, 1, 2, 3);
	}

	@Test
	public void shouldDropBefore() {
		Assertions.assertThat(Interval.of(0, 3).dropBefore(Arithmetic.of(2)))
				.isEqualTo(Interval.of(2, 3));
	}

	@Test
	public void shouldDropBefore1() {
		Assertions.assertThat(Interval.of(0, 3).dropBefore(Arithmetic.of(0)))
				.isEqualTo(Interval.of(0, 3));
	}

	@Test
	public void shouldDropBefore3() {
		Assertions.assertThat(Interval.of(0, 3).dropBefore(Arithmetic.of(3)))
				.isEqualTo(Singleton.of(3));
	}

	@Test
	public void shouldDropBefore4() {
		Assertions.assertThat(Interval.of(0, 3).dropBefore(Arithmetic.of(5)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDropBefore5() {
		Assertions.assertThat(Interval.of(0, 3).dropBefore(Arithmetic.of(-2)))
				.isEqualTo(Interval.of(0, 3));
	}

	@Test
	public void shouldCopyBefore() {
		Assertions.assertThat(Interval.of(0, 3).copyBefore(Arithmetic.of(2)))
				.isEqualTo(Interval.of(0, 1));
	}

	@Test
	public void shouldCopyBefore2() {
		Assertions.assertThat(Interval.of(0, 3).copyBefore(Arithmetic.of(0)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldCopyBefore3() {
		Assertions.assertThat(Interval.of(0, 3).copyBefore(Arithmetic.of(3)))
				.isEqualTo(Interval.of(0, 2));
	}

	@Test
	public void shouldCopyBefore4() {
		Assertions.assertThat(Interval.of(0, 3).copyBefore(Arithmetic.of(5)))
				.isEqualTo(Interval.of(0, 3));
	}

	@Test
	public void shouldDifferenceEnumerated() {
		Union<Integer> expected = Union.of(
				Singleton.of(0),
				Singleton.of(2),
				Singleton.of(4),
				Interval.of(6, 10));
		Assertions.assertThat(
						Interval.of(0, 10)
								.difference(EnumeratedDomain.of(
										Array.of(1, 3, 5)
												.map(Arithmetic::ofCasted))))
				.isEqualTo(
						expected);
	}

	@Test
	public void shouldTestDisjointEmpty() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Empty.instance()))
				.isTrue();
	}

	@Test
	public void shouldTestDisjointSingle() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Singleton.of(3)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSingle2() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Singleton.of(30)))
				.isTrue();
	}

	@Test
	public void shouldTestDisjointSimple() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Interval.of(-3, 1)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSimple2() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Interval.of(9, 11)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSimple3() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Interval.of(11, 30)))
				.isTrue();
	}

	@Test
	public void shouldTestMulti() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Union.of(
								Interval.of(-10, -1),
								Interval.of(11, 30)
						)))
				.isTrue();
	}

	@Test
	public void shouldTestMulti2() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(Union.of(
								Interval.of(-10, 2),
								Interval.of(11, 30)
						)))
				.isFalse();
	}

	@Test
	public void shouldTestEnumerated() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(EnumeratedDomain.of(Array.of(1, 11, 12).map(Arithmetic::of))))
				.isFalse();
	}

	@Test
	public void shouldTestEnumerated2() {
		Assertions.assertThat(Interval.of(0, 10)
						.isDisjoint(EnumeratedDomain.of(Array.of(-1, 11, 12).map(Arithmetic::of))))
				.isTrue();
	}

}