package com.tgac.logic.finitedomain.domains;
import io.vavr.collection.Array;
import org.assertj.core.api.Assertions;
import org.junit.Test;
public class SimpleIntervalTest {
	@Test
	public void shouldSplitIntervalWithSingleton() {
		SimpleInterval<Integer> domain1 = SimpleInterval.of(0, 10);
		Singleton<Integer> domain2 = Singleton.of(5);

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(MultiInterval.of(
						SimpleInterval.of(0, 4),
						SimpleInterval.of(6, 10)));
	}
	@Test
	public void shouldSplitIntervalWithSingletonDisjoint() {
		SimpleInterval<Integer> domain1 = SimpleInterval.of(0, 10);
		Singleton<Integer> domain2 = Singleton.of(11);

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(SimpleInterval.of(0, 10));
	}

	@Test
	public void shouldDiffMultiInterval() {
		SimpleInterval<Integer> domain1 = SimpleInterval.of(0, 10);
		MultiInterval<Integer> domain2 = MultiInterval.of(
				SimpleInterval.of(0, 4), SimpleInterval.of(6, 10));

		Domain<Integer> mergedDomains = domain1.difference(domain2);

		Assertions.assertThat(mergedDomains)
				.isEqualTo(Singleton.of(5));
	}

	@Test
	public void shouldIntersectDomains() {
		SimpleInterval<Integer> domain1 = SimpleInterval.of(0, 5);
		SimpleInterval<Integer> domain2 = SimpleInterval.of(5, 10);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Singleton.of(5));
	}

	@Test
	public void shouldIntersectDomains2() {
		SimpleInterval<Integer> domain1 = SimpleInterval.of(0, 7);
		SimpleInterval<Integer> domain2 = SimpleInterval.of(3, 10);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(SimpleInterval.of(3, 7));
	}

	@Test
	public void shouldIntersectDomains3() {
		SimpleInterval<Integer> domain1 = SimpleInterval.of(0, 7);
		SimpleInterval<Integer> domain2 = SimpleInterval.of(10, 11);

		Assertions.assertThat(domain1.intersect(domain2))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffEqual() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10).difference(SimpleInterval.of(0, 10)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffContaining() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(SimpleInterval.of(-10, 20)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDiffRight() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(SimpleInterval.of(5, 20)))
				.isEqualTo(SimpleInterval.of(0, 4));
	}

	@Test
	public void shouldDiffLeft() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(SimpleInterval.of(-10, 5)))
				.isEqualTo(SimpleInterval.of(6, 10));
	}

	@Test
	public void shouldDiffContained() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(SimpleInterval.of(3, 6)))
				.isEqualTo(MultiInterval.of(
						SimpleInterval.of(0, 2),
						SimpleInterval.of(7, 10)));
	}

	@Test
	public void shouldDiffDisjoint() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(SimpleInterval.of(11, 20)))
				.isEqualTo(SimpleInterval.of(0, 10));
	}

	@Test
	public void shouldDiffEmpty() {
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(Empty.instance()))
				.isEqualTo(SimpleInterval.of(0, 10));
	}

	@Test
	public void shouldStream() {
		Assertions.assertThat(SimpleInterval.of(0, 3).stream())
				.containsExactly(0, 1, 2, 3);
	}

	@Test
	public void shouldDropBefore() {
		Assertions.assertThat(SimpleInterval.of(0, 3).dropBefore(Arithmetic.of(2)))
				.isEqualTo(SimpleInterval.of(2, 3));
	}

	@Test
	public void shouldDropBefore1() {
		Assertions.assertThat(SimpleInterval.of(0, 3).dropBefore(Arithmetic.of(0)))
				.isEqualTo(SimpleInterval.of(0, 3));
	}

	@Test
	public void shouldDropBefore3() {
		Assertions.assertThat(SimpleInterval.of(0, 3).dropBefore(Arithmetic.of(3)))
				.isEqualTo(Singleton.of(3));
	}

	@Test
	public void shouldDropBefore4() {
		Assertions.assertThat(SimpleInterval.of(0, 3).dropBefore(Arithmetic.of(5)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldDropBefore5() {
		Assertions.assertThat(SimpleInterval.of(0, 3).dropBefore(Arithmetic.of(-2)))
				.isEqualTo(SimpleInterval.of(0, 3));
	}

	@Test
	public void shouldCopyBefore() {
		Assertions.assertThat(SimpleInterval.of(0, 3).copyBefore(Arithmetic.of(2)))
				.isEqualTo(SimpleInterval.of(0, 1));
	}

	@Test
	public void shouldCopyBefore2() {
		Assertions.assertThat(SimpleInterval.of(0, 3).copyBefore(Arithmetic.of(0)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldCopyBefore3() {
		Assertions.assertThat(SimpleInterval.of(0, 3).copyBefore(Arithmetic.of(3)))
				.isEqualTo(SimpleInterval.of(0, 2));
	}

	@Test
	public void shouldCopyBefore4() {
		Assertions.assertThat(SimpleInterval.of(0, 3).copyBefore(Arithmetic.of(5)))
				.isEqualTo(SimpleInterval.of(0, 3));
	}

	@Test
	public void shouldDifferenceEnumerated() {
		MultiInterval<Integer> expected = MultiInterval.of(
				Singleton.of(0),
				Singleton.of(2),
				Singleton.of(4),
				SimpleInterval.of(6, 10));
		Assertions.assertThat(
						SimpleInterval.of(0, 10)
								.difference(EnumeratedInterval.of(
										Array.of(1, 3, 5)
												.map(Arithmetic::ofCasted))))
				.isEqualTo(
						expected);
	}

	@Test
	public void shouldTestDisjointEmpty() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(Empty.instance()))
				.isTrue();
	}

	@Test
	public void shouldTestDisjointSingle() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(Singleton.of(3)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSingle2() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(Singleton.of(30)))
				.isTrue();
	}

	@Test
	public void shouldTestDisjointSimple() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(SimpleInterval.of(-3, 1)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSimple2() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(SimpleInterval.of(9, 11)))
				.isFalse();
	}

	@Test
	public void shouldTestDisjointSimple3() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(SimpleInterval.of(11, 30)))
				.isTrue();
	}

	@Test
	public void shouldTestMulti() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(MultiInterval.of(
								SimpleInterval.of(-10, -1),
								SimpleInterval.of(11, 30)
						)))
				.isTrue();
	}

	@Test
	public void shouldTestMulti2() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(MultiInterval.of(
								SimpleInterval.of(-10, 2),
								SimpleInterval.of(11, 30)
						)))
				.isFalse();
	}

	@Test
	public void shouldTestEnumerated() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(EnumeratedInterval.of(Array.of(1, 11, 12).map(Arithmetic::of))))
				.isFalse();
	}

	@Test
	public void shouldTestEnumerated2() {
		Assertions.assertThat(SimpleInterval.of(0, 10)
						.isDisjoint(EnumeratedInterval.of(Array.of(-1, 11, 12).map(Arithmetic::of))))
				.isTrue();
	}

}