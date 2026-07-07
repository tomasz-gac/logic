package com.tgac.logic.finitedomain.domains;

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.Domain;
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
		return EnumeratedDomain.of(Arrays.stream(vs)
				.mapToObj(Arithmetic::of)
				.collect(Collectors.toList()));
	}

	private static List<Long> values(Domain<Long> d) {
		return d.stream().collect(Collectors.toList());
	}

	@Test
	public void enumeratedAtMostKeepsTheBound() {
		assertThat(values(enumerated(1, 2, 3).atMost(Arithmetic.of(2L)))).containsExactly(1L, 2L);
	}

	@Test
	public void enumeratedAtMostAtMinKeepsMin() {
		assertThat(values(enumerated(1, 2, 3).atMost(Arithmetic.of(1L)))).containsExactly(1L);
	}

	@Test
	public void enumeratedAtMostBelowMinIsEmpty() {
		assertThat(enumerated(1, 2, 3).atMost(Arithmetic.of(0L)).isEmpty()).isTrue();
	}

	@Test
	public void enumeratedAtMostBetweenElementsNarrows() {
		// 4 is not an element; everything above it must still go
		assertThat(values(enumerated(1, 3, 5).atMost(Arithmetic.of(4L)))).containsExactly(1L, 3L);
	}

	@Test
	public void enumeratedAtLeastKeepsTheBound() {
		assertThat(values(enumerated(1, 2, 3).atLeast(Arithmetic.of(2L)))).containsExactly(2L, 3L);
	}

	@Test
	public void enumeratedAtLeastBetweenElementsNarrows() {
		assertThat(values(enumerated(1, 3, 5).atLeast(Arithmetic.of(2L)))).containsExactly(3L, 5L);
	}

	@Test
	public void enumeratedAtLeastAboveMaxIsEmpty() {
		assertThat(enumerated(1, 2, 3).atLeast(Arithmetic.of(4L)).isEmpty()).isTrue();
	}

	@Test
	public void intervalAtMostKeepsTheBound() {
		assertThat(values(Interval.of(1L, 10L).atMost(Arithmetic.of(3L)))).containsExactly(1L, 2L, 3L);
	}

	@Test
	public void intervalAtMostAtMinKeepsMin() {
		assertThat(values(Interval.of(1L, 10L).atMost(Arithmetic.of(1L)))).containsExactly(1L);
	}

	@Test
	public void intervalAtMostBelowMinIsEmpty() {
		assertThat(Interval.of(1L, 10L).atMost(Arithmetic.of(0L)).isEmpty()).isTrue();
	}

	@Test
	public void intervalAtMostAboveMaxIsUnchanged() {
		assertThat(Interval.of(1L, 10L).atMost(Arithmetic.of(15L)).contains(10L)).isTrue();
	}

	@Test
	public void intervalAtLeastKeepsTheBound() {
		assertThat(values(Interval.of(1L, 10L).atLeast(Arithmetic.of(10L)))).containsExactly(10L);
	}

	@Test
	public void singletonAgreesWithTheInclusiveSemantics() {
		Domain<Long> two = Singleton.of(2L);
		assertThat(values(two.atMost(Arithmetic.of(2L)))).containsExactly(2L);
		assertThat(two.atMost(Arithmetic.of(1L)).isEmpty()).isTrue();
		assertThat(values(two.atLeast(Arithmetic.of(2L)))).containsExactly(2L);
		assertThat(two.atLeast(Arithmetic.of(3L)).isEmpty()).isTrue();
	}

	@Test
	public void unionDropsMembersNarrowedToEmpty() {
		Domain<Long> u = Union.of(Interval.of(1L, 3L), Interval.of(6L, 9L));
		assertThat(values(u.atMost(Arithmetic.of(4L)))).containsExactly(1L, 2L, 3L);
		assertThat(values(u.atMost(Arithmetic.of(7L)))).containsExactly(1L, 2L, 3L, 6L, 7L);
		assertThat(u.atLeast(Arithmetic.of(10L)).isEmpty()).isTrue();
	}
}
