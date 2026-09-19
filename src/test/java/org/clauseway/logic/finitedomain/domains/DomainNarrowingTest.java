package org.clauseway.logic.finitedomain.domains;

import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.Longs;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * atMost/atLeast are inclusive bound narrowings — the primitives leq relies on.
 * Every domain type must agree on the semantics: atMost(e) keeps values ≤ e,
 * atLeast(e) keeps values ≥ e, including when e falls between the elements of a
 * sparse domain or empties a union member.
 */
public class DomainNarrowingTest {

	private static Domain<Long> enumerated(long... vs) {
		return Longs.enumerated(Arrays.stream(vs)
				.boxed()
				.toArray(Long[]::new));
	}

	private static List<Long> values(Domain<Long> d) {
		return d.stream().collect(Collectors.toList());
	}

	@Test
	public void enumeratedAtMostKeepsTheBound() {
		assertThat(values(enumerated(1, 2, 3).atMost(2L))).containsExactly(1L, 2L);
	}

	@Test
	public void enumeratedAtMostAtMinKeepsMin() {
		assertThat(values(enumerated(1, 2, 3).atMost(1L))).containsExactly(1L);
	}

	@Test
	public void enumeratedAtMostBelowMinIsEmpty() {
		assertThat(enumerated(1, 2, 3).atMost(0L).isEmpty()).isTrue();
	}

	@Test
	public void enumeratedAtMostBetweenElementsNarrows() {
		// 4 is not an element; everything above it must still go
		assertThat(values(enumerated(1, 3, 5).atMost(4L))).containsExactly(1L, 3L);
	}

	@Test
	public void enumeratedAtLeastKeepsTheBound() {
		assertThat(values(enumerated(1, 2, 3).atLeast(2L))).containsExactly(2L, 3L);
	}

	@Test
	public void enumeratedAtLeastBetweenElementsNarrows() {
		assertThat(values(enumerated(1, 3, 5).atLeast(2L))).containsExactly(3L, 5L);
	}

	@Test
	public void enumeratedAtLeastAboveMaxIsEmpty() {
		assertThat(enumerated(1, 2, 3).atLeast(4L).isEmpty()).isTrue();
	}

	@Test
	public void intervalAtMostKeepsTheBound() {
		assertThat(values(Longs.interval(1, 10).atMost(3L))).containsExactly(1L, 2L, 3L);
	}

	@Test
	public void intervalAtMostAtMinKeepsMin() {
		assertThat(values(Longs.interval(1, 10).atMost(1L))).containsExactly(1L);
	}

	@Test
	public void intervalAtMostBelowMinIsEmpty() {
		assertThat(Longs.interval(1, 10).atMost(0L).isEmpty()).isTrue();
	}

	@Test
	public void intervalAtMostAboveMaxIsUnchanged() {
		assertThat(Longs.interval(1, 10).atMost(15L).contains(10L)).isTrue();
	}

	@Test
	public void intervalAtLeastKeepsTheBound() {
		assertThat(values(Longs.interval(1, 10).atLeast(10L))).containsExactly(10L);
	}

	@Test
	public void singletonAgreesWithTheInclusiveSemantics() {
		Domain<Long> two = Longs.singleton(2);
		assertThat(values(two.atMost(2L))).containsExactly(2L);
		assertThat(two.atMost(1L).isEmpty()).isTrue();
		assertThat(values(two.atLeast(2L))).containsExactly(2L);
		assertThat(two.atLeast(3L).isEmpty()).isTrue();
	}

	@Test
	public void unionDropsMembersNarrowedToEmpty() {
		Domain<Long> u = Union.of(Longs.interval(1, 3), Longs.interval(6, 9));
		assertThat(values(u.atMost(4L))).containsExactly(1L, 2L, 3L);
		assertThat(values(u.atMost(7L))).containsExactly(1L, 2L, 3L, 6L, 7L);
		assertThat(u.atLeast(10L).isEmpty()).isTrue();
	}
}
