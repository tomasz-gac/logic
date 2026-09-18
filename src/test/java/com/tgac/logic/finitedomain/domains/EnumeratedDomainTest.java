package com.tgac.logic.finitedomain.domains;

import com.tgac.logic.finitedomain.Domain;
import com.tgac.logic.finitedomain.Ints;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class EnumeratedDomainTest {
	private static final Domain<Integer> INTERVAL =
			Ints.enumerated(2, 3, 5);

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
		Assertions.assertThat(INTERVAL.difference(Ints.interval(4, 10)))
				.isEqualTo(Ints.enumerated(2, 3));
	}

	@Test
	public void shouldDiff2() {
		Assertions.assertThat(INTERVAL.difference(Ints.interval(3, 10)))
				.isEqualTo(Ints.singleton(2));
	}

	@Test
	public void shouldIntersect() {
		Assertions.assertThat(INTERVAL.intersect(Ints.interval(3, 10)))
				.isEqualTo(Ints.enumerated(3, 5));
	}

	@Test
	public void shouldIntersect2() {
		Assertions.assertThat(INTERVAL.intersect(Ints.interval(4, 10)))
				.isEqualTo(Ints.singleton(5));
	}

	@Test
	public void shouldNotBeDisjoint() {
		Assertions.assertThat(INTERVAL.isDisjoint(Ints.interval(4, 10)))
				.isFalse();
	}

	@Test
	public void shouldBeDisjoint() {
		Assertions.assertThat(INTERVAL.isDisjoint(
						Ints.enumerated(4, 6)))
				.isTrue();
	}

	@Test
	public void shouldAtLeast() {
		Assertions.assertThat(INTERVAL.atLeast(3))
				.isEqualTo(Ints.enumerated(3, 5));
	}

	@Test
	public void shouldAtLeast2() {
		Assertions.assertThat(INTERVAL.atLeast(5))
				.isEqualTo(Ints.singleton(5));
	}

	@Test
	public void shouldAtLeast3() {
		Assertions.assertThat(INTERVAL.atLeast(15))
				.isEqualTo(Empty.instance());
	}

	@Test
	public void shouldAtLeast4() {
		Assertions.assertThat(INTERVAL.atLeast(1))
				.isEqualTo(INTERVAL);
	}

	@Test
	public void shouldAtMost() {
		Assertions.assertThat(INTERVAL.atMost(5))
				.isEqualTo(INTERVAL);
	}

	@Test
	public void shouldAtMost2() {
		Assertions.assertThat(INTERVAL.atMost(3))
				.isEqualTo(Ints.enumerated(2, 3));
	}

	@Test
	public void shouldAtMost3() {
		Assertions.assertThat(INTERVAL.atMost(6))
				.isEqualTo(INTERVAL);
	}

	@Test
	public void shouldAtMost4() {
		Assertions.assertThat(INTERVAL.atMost(1))
				.isEqualTo(Empty.instance());
	}
}