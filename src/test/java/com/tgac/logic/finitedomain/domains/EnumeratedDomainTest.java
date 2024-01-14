package com.tgac.logic.finitedomain.domains;
import io.vavr.collection.Array;
import org.assertj.core.api.Assertions;
import org.junit.Test;
public class EnumeratedDomainTest {
	private static final EnumeratedDomain<Integer> INTERVAL =
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
}