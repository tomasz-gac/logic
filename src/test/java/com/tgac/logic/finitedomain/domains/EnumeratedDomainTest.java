package com.tgac.logic.finitedomain.domains;

import com.tgac.logic.finitedomain.Domain;
import io.vavr.collection.Array;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class EnumeratedDomainTest {
	private static final Domain<Integer> INTERVAL =
			EnumeratedDomain.of(Array.of(2, 3, 5).map(Arithmetic::of));

	@Test
	public void shouldNotContain() {
		Assertions.assertThat(INTERVAL.contains(1)).isFalse();
	}

	@Test
	public void shouldContain() {
		Assertions.assertThat(INTERVAL.contains(2)).isTrue();
	}

	@Test
	public void shouldDiff() {
		Assertions.assertThat(INTERVAL.difference(Interval.of(4, 10)))
				.isEqualTo(EnumeratedDomain.of(Array.of(2, 3)
						.map(Arithmetic::of)));
	}

	@Test
	public void shouldDiff2() {
		Assertions.assertThat(INTERVAL.difference(Interval.of(3, 10)))
				.isEqualTo(Singleton.of(2));
	}

	@Test
	public void shouldIntersect() {
		Assertions.assertThat(INTERVAL.intersect(Interval.of(3, 10)))
				.isEqualTo(EnumeratedDomain.of(Array.of(3, 5).map(Arithmetic::of)));
	}

	@Test
	public void shouldIntersect2() {
		Assertions.assertThat(INTERVAL.intersect(Interval.of(4, 10)))
				.isEqualTo(Singleton.of(5));
	}

	@Test
	public void shouldNotBeDisjoint() {
		Assertions.assertThat(INTERVAL.isDisjoint(Interval.of(4, 10)))
				.isFalse();
	}

	@Test
	public void shouldBeDisjoint() {
		Assertions.assertThat(INTERVAL.isDisjoint(
						EnumeratedDomain.of(Array.of(4, 6).map(Arithmetic::of))))
				.isTrue();
	}

	@Test
	public void shouldAtLeast() {
		Assertions.assertThat(INTERVAL.atLeast(Arithmetic.of(3)))
				.isEqualTo(EnumeratedDomain.of(
						Array.of(3, 5).map(Arithmetic::of)));
	}

	@Test
	public void shouldAtLeast2() {
		Assertions.assertThat(INTERVAL.atLeast(Arithmetic.of(5)))
				.isEqualTo(Singleton.of(5));
	}

	@Test
	public void shouldAtLeast3() {
		Assertions.assertThat(INTERVAL.atLeast(Arithmetic.of(15)))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldAtLeast4() {
		Assertions.assertThat(INTERVAL.atLeast(Arithmetic.of(1)))
				.isEqualTo(INTERVAL);
	}

	@Test
	public void shouldAtMost() {
		Assertions.assertThat(INTERVAL.atMost(Arithmetic.of(5)))
				.isEqualTo(INTERVAL);
	}

	@Test
	public void shouldAtMost2() {
		Assertions.assertThat(INTERVAL.atMost(Arithmetic.of(3)))
				.isEqualTo(EnumeratedDomain.of(
						Array.of(2, 3).map(Arithmetic::of)));
	}

	@Test
	public void shouldAtMost3() {
		Assertions.assertThat(INTERVAL.atMost(Arithmetic.of(6)))
				.isEqualTo(INTERVAL);
	}

	@Test
	public void shouldAtMost4() {
		Assertions.assertThat(INTERVAL.atMost(Arithmetic.of(1)))
				.isEqualTo(Empty.instance());
	}
}