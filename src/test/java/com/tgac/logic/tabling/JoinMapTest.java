package com.tgac.logic.tabling;

// ABOUTME: JoinMap's contract: fresh keys append in arrival order, known keys fold
// ABOUTME: by the semiring, and the log records exactly the arrivals that ascended.

import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.algebra.Semirings;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.Test;

public class JoinMapTest {

	private static JoinMap<String, Long> shortest(String key, long cost) {
		return JoinMap.<String, Long> empty(Semirings.MIN_PLUS).append(key, cost).get();
	}

	private static final Condition WIDE = Condition.of(Span.factor(0L, 10L));
	private static final Condition NARROW = Condition.of(Span.factor(3L, 6L));

	private static JoinMap<String, Condition> regions(String key, Condition c) {
		return JoinMap.<String, Condition> empty(Condition.RING).append(key, c).get();
	}

	@Test
	public void joinRefusesAcrossRings() {
		// two structurally identical but DISTINCT ring instances: folding one
		// map's log under another map's ring silently mixes algebras - refuse
		JoinMap<String, Long> mine = JoinMap.empty(Semirings.MIN_PLUS);
		JoinMap<String, Long> foreign = JoinMap.<String, Long> empty(new Semirings.MinPlusSemiring())
				.append("a", 1L).get();

		assertThatThrownBy(() -> mine.join(foreign))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ring");
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

	@Test
	public void freshKeysKeepArrivalOrderUnderTheIndex() {
		JoinMap<String, Long> map = shortest("a", 1L).append("b", 2L).get();

		assertThat(map.get(0)._1).isEqualTo("a");
		assertThat(map.get(1)._1).isEqualTo("b");
		assertThat(map.get(2)).isNull();
	}

	@Test
	public void theLogRecordsEveryAscentAndOnlyAscents() {
		JoinMap<String, Long> map = shortest("d", 6L)
				.append("d", 4L).get();
		JoinMap<String, Long> after = map.append("d", 9L).getOrElse(map);

		// two ascents of one key: one order slot, two log events
		assertThat(after.size()).isEqualTo(1);
		assertThat(after.logSize()).isEqualTo(2);
		assertThat(after.logAt(0)).isEqualTo(io.vavr.Tuple.of("d", 6L));
		assertThat(after.logAt(1)).isEqualTo(io.vavr.Tuple.of("d", 4L));
	}

	@Test
	public void absorbedByIsPointwiseValueAbsorption() {
		// the cheaper fold already contains the dearer one; a missing key
		// or a better value on my side refuses
		assertThat(shortest("d", 6L).absorbedBy(shortest("d", 4L))).isTrue();
		assertThat(shortest("d", 4L).absorbedBy(shortest("d", 6L))).isFalse();
		assertThat(shortest("d", 4L).absorbedBy(shortest("e", 4L))).isFalse();
		assertThat(shortest("d", 6L)
				.absorbedBy(shortest("d", 4L).append("e", 1L).get())).isTrue();
	}

	@Test
	public void conditionValuesFoldBySubsumption() {
		JoinMap<String, Condition> map = regions("t", NARROW).append("t", WIDE).get();

		// the value ascended to the wider region; the narrow re-arrival is inert
		assertThat(map.members.get("t").get()).isEqualTo(WIDE);
		assertThat(map.logSize()).isEqualTo(2);
		assertThat(map.append("t", NARROW).isEmpty()).isTrue();
	}

	@Test
	public void aGroundArrivalAbsorbsEveryConditionalOne() {
		JoinMap<String, Condition> map = regions("t", NARROW).append("t", Condition.ONE).get();

		// 1 ⊕ a = 1: the term is now a fact and nothing can ever move it
		assertThat(map.isTop(map.members.get("t").get())).isTrue();
		assertThat(map.append("t", WIDE).isEmpty()).isTrue();
	}
}
