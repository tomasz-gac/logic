package com.tgac.logic.tabling;

// ABOUTME: Antichain's contract: a dominated newcomer is absorbed, a dominating
// ABOUTME: newcomer evicts what it covers, and the eviction is an ascent.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.Test;

public class AntichainTest {

	/** [lo, hi] interval keys; a covers b when it contains it. */
	private static final Antichain.Dominance<Tuple2<Integer, Integer>> CONTAINS =
			(a, b) -> a._1 <= b._1 && b._2 <= a._2;

	private static Antichain<Tuple2<Integer, Integer>, Boolean> regions() {
		return Antichain.empty(Semirings.BOOLEAN, CONTAINS);
	}

	@Test
	public void aDominatedNewcomerIsAbsorbed() {
		Antichain<Tuple2<Integer, Integer>, Boolean> wide = regions().append(Tuple.of(1, 3), true).get();

		assertThat(wide.append(Tuple.of(1, 2), true).isEmpty()).isTrue();
	}

	@Test
	public void aDominatingNewcomerEvictsAndThatIsAnAscent() {
		Antichain<Tuple2<Integer, Integer>, Boolean> narrow = regions().append(Tuple.of(1, 2), true).get();
		Antichain<Tuple2<Integer, Integer>, Boolean> grown = narrow.append(Tuple.of(1, 3), true).get();

		// the set got no bigger, the licensed region did: the downset grew
		assertThat(grown.elements()).hasSize(1);
		assertThat(grown.elements().get(0)._1).isEqualTo(Tuple.of(1, 3));
		assertThat(grown).isNotEqualTo(narrow);
	}

	@Test
	public void incomparableRegionsCoexist() {
		Antichain<Tuple2<Integer, Integer>, Boolean> chain = regions()
				.append(Tuple.of(1, 2), true).get()
				.append(Tuple.of(5, 6), true).get();

		assertThat(chain.elements()).hasSize(2);
	}

	@Test
	public void arrivalOrderCannotShapeTheAntichain() {
		Antichain<Tuple2<Integer, Integer>, Boolean> narrowFirst = regions()
				.append(Tuple.of(1, 2), true).get()
				.append(Tuple.of(1, 3), true).get();
		Antichain<Tuple2<Integer, Integer>, Boolean> wideFirst = regions()
				.append(Tuple.of(1, 3), true).get();
		// narrow-after-wide is absorbed entirely
		assertThat(wideFirst.append(Tuple.of(1, 2), true).isEmpty()).isTrue();

		assertThat(narrowFirst).isEqualTo(wideFirst);
	}

	@Test
	public void joinFoldsThroughTheSameAntichainStep() {
		Antichain<Tuple2<Integer, Integer>, Boolean> narrow = regions().append(Tuple.of(1, 2), true).get();
		Antichain<Tuple2<Integer, Integer>, Boolean> wide = regions().append(Tuple.of(1, 3), true).get();

		assertThat(narrow.join(wide)).isEqualTo(wide.join(narrow));
		assertThat(narrow.join(wide).elements()).hasSize(1);
	}
}
