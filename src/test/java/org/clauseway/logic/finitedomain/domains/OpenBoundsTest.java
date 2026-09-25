package org.clauseway.logic.finitedomain.domains;

// ABOUTME: Open/closed endpoints: dense difference and strict narrowing become
// ABOUTME: honest without stepping; discrete bounds canonicalize to closed form.

import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.finitedomain.BigDecimals;
import org.clauseway.logic.finitedomain.Bound;
import org.clauseway.logic.finitedomain.Domain;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.finitedomain.capabilities.Discrete;
import org.clauseway.vavr.control.Option;
import java.math.BigDecimal;
import java.util.Comparator;
import org.junit.Test;

public class OpenBoundsTest {

	private static final Comparator<BigDecimal> ORDER = Comparator.naturalOrder();

	private static Domain<BigDecimal> dense(String min, String max) {
		return BigDecimals.interval(new BigDecimal(min), new BigDecimal(max));
	}

	private static BigDecimal d(String v) {
		return new BigDecimal(v);
	}

	@Test
	public void denseDifferenceCutsABoundaryPoint() {
		// the doubt at difference/visit(Singleton): [0,1] − {1} over decimals
		// is [0,1) — the point leaves, everything below it stays
		Domain<BigDecimal> cut = dense("0", "1")
				.difference(BigDecimals.singleton(d("1")));

		assertThat(cut.contains(d("1"))).isFalse();
		assertThat(cut.contains(d("0.999"))).isTrue();
		assertThat(cut.contains(d("0"))).isTrue();
		assertThat(cut.isEmpty()).isFalse();
	}

	@Test
	public void denseDifferenceCutsAnInteriorPoint() {
		Domain<BigDecimal> cut = dense("0", "1")
				.difference(BigDecimals.singleton(d("0.5")));

		assertThat(cut.contains(d("0.5"))).isFalse();
		assertThat(cut.contains(d("0.4"))).isTrue();
		assertThat(cut.contains(d("0.6"))).isTrue();
		assertThat(cut.contains(d("0"))).isTrue();
		assertThat(cut.contains(d("1"))).isTrue();
	}

	@Test
	public void denseDifferenceTrimsAtAClosedEdge() {
		// the doubt at difference/visit(Interval): [0,10] − [3,6] = [0,3) ∪ (6,10]
		Domain<BigDecimal> cut = dense("0", "10").difference(dense("3", "6"));

		assertThat(cut.contains(d("3"))).isFalse();
		assertThat(cut.contains(d("6"))).isFalse();
		assertThat(cut.contains(d("2.9"))).isTrue();
		assertThat(cut.contains(d("6.1"))).isTrue();
	}

	@Test
	public void denseDifferenceKeepsTheComplementOfOpenEdges() {
		// removing the OPEN interval (3,6) keeps its endpoints
		Domain<BigDecimal> open = Interval.of(
				Bound.open(d("3")), Bound.open(d("6")),
				ORDER, Option.<Discrete<BigDecimal>> none());
		Domain<BigDecimal> cut = dense("0", "10").difference(open);

		assertThat(cut.contains(d("3"))).isTrue();
		assertThat(cut.contains(d("6"))).isTrue();
		assertThat(cut.contains(d("4"))).isFalse();
	}

	@Test
	public void denseStrictNarrowingExcludesTheCut() {
		Domain<BigDecimal> below = dense("0", "2.5")
				.atMost(Bound.open(d("2.5")));

		assertThat(below.contains(d("2.5"))).isFalse();
		assertThat(below.contains(d("2.4"))).isTrue();

		Domain<BigDecimal> above = dense("0", "2.5")
				.atLeast(Bound.open(d("0")));

		assertThat(above.contains(d("0"))).isFalse();
		assertThat(above.contains(d("0.1"))).isTrue();
	}

	@Test
	public void denseOpenPointIsEmpty() {
		// (x, x) admits nothing; [x, x) admits nothing; [x, x] is the point
		assertThat(dense("0", "1").atLeast(Bound.open(d("1"))).isEmpty()).isTrue();
		assertThat(dense("0", "1")
				.atMost(Bound.open(d("1")))
				.atLeast(Bound.closed(d("1")))
				.isEmpty()).isTrue();
	}

	@Test
	public void intersectionKeepsTheTighterEndpoint() {
		// [0, 1) ∩ (0, 1] = (0, 1): open beats closed at equal values
		Domain<BigDecimal> halfDown = Interval.of(
				Bound.closed(d("0")), Bound.open(d("1")),
				ORDER, Option.<Discrete<BigDecimal>> none());
		Domain<BigDecimal> halfUp = Interval.of(
				Bound.open(d("0")), Bound.closed(d("1")),
				ORDER, Option.<Discrete<BigDecimal>> none());

		Domain<BigDecimal> both = halfDown.intersect(halfUp);

		assertThat(both.contains(d("0"))).isFalse();
		assertThat(both.contains(d("1"))).isFalse();
		assertThat(both.contains(d("0.5"))).isTrue();
	}

	@Test
	public void touchingHalfOpenIntervalsAreDisjointOnlyWithoutTheSharedPoint() {
		Domain<BigDecimal> upTo = Interval.of(
				Bound.closed(d("0")), Bound.open(d("1")),
				ORDER, Option.<Discrete<BigDecimal>> none());
		Domain<BigDecimal> from = dense("1", "2");
		Domain<BigDecimal> after = Interval.of(
				Bound.open(d("1")), Bound.closed(d("2")),
				ORDER, Option.<Discrete<BigDecimal>> none());

		// [0,1) and [1,2] share no point; [0,1] and [1,2] share 1
		assertThat(upTo.isDisjoint(from)).isTrue();
		assertThat(dense("0", "1").isDisjoint(from)).isFalse();
		assertThat(upTo.isDisjoint(after)).isTrue();
	}

	@Test
	public void denseUnionMergesOnTouchAndKeepsGaps() {
		// [0,1) ∪ [1,2] is contiguous; [0,1) ∪ (1,2] has a hole at 1
		Domain<BigDecimal> touching = Union.of(
				Interval.of(Bound.closed(d("0")), Bound.open(d("1")),
						ORDER, Option.<Discrete<BigDecimal>> none()),
				dense("1", "2"));
		assertThat(touching.contains(d("1"))).isTrue();
		assertThat(touching instanceof Union ?
				((Union<BigDecimal>) touching).getIntervals().size() : 1).isEqualTo(1);

		Union<BigDecimal> holed = Union.of(
				Interval.of(Bound.closed(d("0")), Bound.open(d("1")),
						ORDER, Option.<Discrete<BigDecimal>> none()),
				Interval.of(Bound.open(d("1")), Bound.closed(d("2")),
						ORDER, Option.<Discrete<BigDecimal>> none()));
		assertThat(holed.contains(d("1"))).isFalse();
		assertThat(holed.getIntervals()).hasSize(2);
	}

	@Test
	public void discreteBoundsCanonicalizeToClosedForm() {
		// a type with a step seat never carries open bounds: (1, 5) over ints
		// IS [2, 4] — one spelling per value set, the identity the equal-domain
		// guard and answer keys rely on
		Domain<Integer> viaOpen = Interval.of(
				Bound.open(1), Bound.open(5),
				Comparator.<Integer> naturalOrder(), Option.of(Discrete.INTS));

		assertThat(viaOpen).isEqualTo(Ints.interval(2, 4));
	}

	@Test
	public void discreteStrictNarrowingStillStepsInward() {
		// the narrowing door normalizes with the DOMAIN's own step: ints
		// atMost(open 5) is atMost(4) — bit-identical to the stepped past
		assertThat(Ints.interval(0, 9).atMost(Bound.open(5)))
				.isEqualTo(Ints.interval(0, 4));
		assertThat(Ints.interval(0, 9).atLeast(Bound.open(5)))
				.isEqualTo(Ints.interval(6, 9));
	}

	@Test
	public void discreteDifferenceIsUnchanged() {
		assertThat(Ints.interval(0, 10).difference(Ints.singleton(5)))
				.isEqualTo(Union.of(Ints.interval(0, 4), Ints.interval(6, 10)));
	}
}
