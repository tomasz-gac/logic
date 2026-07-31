package com.tgac.logic.tabling;

// ABOUTME: JoinMap's contract: folds ascend or absorb; under a Dominance the
// ABOUTME: partial region is the maximal antichain, and an eviction is an ascent.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import io.vavr.Tuple2;
import org.junit.Test;

public class JoinMapTest {

	private static JoinMap<String, Long> shortest(String key, long cost) {
		return JoinMap.<String, Long> empty(Semirings.MIN_PLUS).append(key, cost).get();
	}

	@Test
	public void anAscendingFoldMovesTheValueWithoutANewKey() {
		JoinMap<String, Long> map = shortest("d", 6L).join(shortest("d", 4L));

		// the key set did not grow, the knowledge did: min(6, 4) moved the value
		assertThat(map.size()).isEqualTo(1);
		assertThat(map.members.get("d").get()).isEqualTo(4L);
	}

	@Test
	public void anAbsorbedFoldChangesNothing() {
		JoinMap<String, Long> map = shortest("d", 4L).join(shortest("d", 6L));

		assertThat(map.members.get("d").get()).isEqualTo(4L);
	}

	@Test
	public void appendAbsorbedRefuses() {
		assertThat(shortest("d", 4L).append("d", 6L).isEmpty()).isTrue();
	}

	// ---- the partial region: intervals under containment ----

	/** [lo, hi] interval keys; a covers b when it contains it. */
	private static final JoinMap.Dominance<int[]> CONTAINS = new JoinMap.Dominance<int[]>() {
		@Override
		public boolean partial(int[] key) {
			return true;
		}

		@Override
		public boolean dominates(int[] a, int[] b) {
			return a[0] <= b[0] && b[1] <= a[1];
		}
	};

	private static JoinMap<int[], Boolean> regions() {
		return JoinMap.empty(Semirings.BOOLEAN, CONTAINS);
	}

	@Test
	public void aDominatedNewcomerIsAbsorbed() {
		JoinMap<int[], Boolean> wide = regions().append(new int[] {1, 3}, true).get();

		assertThat(wide.append(new int[] {1, 2}, true).isEmpty()).isTrue();
	}

	@Test
	public void aDominatingNewcomerEvictsAndThatIsAnAscent() {
		JoinMap<int[], Boolean> narrow = regions().append(new int[] {1, 2}, true).get();
		JoinMap<int[], Boolean> grown = narrow.append(new int[] {1, 3}, true).get();

		// the set got no bigger, the licensed region did: the downset grew
		assertThat(grown.partial()).hasSize(1);
		assertThat(grown.partial().get(0)._1).containsExactly(1, 3);
		assertThat(grown).isNotEqualTo(narrow);
	}

	@Test
	public void incomparableRegionsCoexist() {
		JoinMap<int[], Boolean> map = regions()
				.append(new int[] {1, 2}, true).get()
				.append(new int[] {5, 6}, true).get();

		assertThat(map.partial()).hasSize(2);
	}

	@Test
	public void partialKeysStayOutOfTheIndexedOrder() {
		JoinMap<int[], Boolean> map = regions().append(new int[] {1, 2}, true).get();

		// atoms are the indexed, cursor-stable enumeration; the partial
		// region is read whole, never by index
		assertThat(map.size()).isEqualTo(0);
		assertThat(map.partial()).hasSize(1);
	}

	@Test
	public void arrivalOrderCannotShapeThePartialRegion() {
		JoinMap<int[], Boolean> narrowFirst = regions()
				.append(new int[] {1, 2}, true).get()
				.append(new int[] {1, 3}, true).get();
		JoinMap<int[], Boolean> wideFirst = regions()
				.append(new int[] {1, 3}, true).get();
		// narrow-after-wide is absorbed entirely
		assertThat(wideFirst.append(new int[] {1, 2}, true).isEmpty()).isTrue();

		assertThat(narrowFirst.partial()).hasSize(1);
		assertThat(narrowFirst.partial().get(0)._1).containsExactly(1, 3);
		assertThat(wideFirst.partial().get(0)._1).containsExactly(1, 3);
	}

	@Test
	public void joinFoldsThePartialRegionThroughTheSameAntichainStep() {
		JoinMap<int[], Boolean> narrow = regions().append(new int[] {1, 2}, true).get();
		JoinMap<int[], Boolean> wide = regions().append(new int[] {1, 3}, true).get();

		assertThat(narrow.join(wide).partial()).hasSize(1);
		assertThat(wide.join(narrow).partial()).hasSize(1);
		for (Tuple2<int[], Boolean> live : narrow.join(wide).partial()) {
			assertThat(live._1).containsExactly(1, 3);
		}
	}
}
