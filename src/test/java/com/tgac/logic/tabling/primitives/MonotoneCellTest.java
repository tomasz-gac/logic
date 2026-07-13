package com.tgac.logic.tabling.primitives;

// ABOUTME: Pins the monotone cell's contract: grow swaps and drains all parked,
// ABOUTME: a refused step changes nothing, park races grow toward reading.

import static org.assertj.core.api.Assertions.assertThat;

import io.vavr.control.Option;
import org.junit.Test;

public class MonotoneCellTest {

	@Test
	public void growSwapsTheValueAndDrainsAllParked() {
		MonotoneCell<Integer, String> cell = new MonotoneCell<>(0);
		assertThat(cell.park("a", v -> v == 0)).isTrue();
		assertThat(cell.park("b", v -> v == 0)).isTrue();

		Option<io.vavr.collection.List<String>> drained = cell.grow(v -> Option.of(v + 1));

		assertThat(drained.isDefined()).isTrue();
		assertThat(drained.get()).containsExactly("a", "b");
		assertThat(cell.read()).isEqualTo(1);
		assertThat(cell.parkedCount()).isEqualTo(0);
	}

	@Test
	public void aRefusedStepChangesNothingAndWakesNobody() {
		MonotoneCell<Integer, String> cell = new MonotoneCell<>(7);
		cell.park("sleeper", v -> true);

		assertThat(cell.grow(v -> Option.none()).isDefined()).isFalse();
		assertThat(cell.read()).isEqualTo(7);
		assertThat(cell.parkedCount()).isEqualTo(1);
	}

	@Test
	public void parkRefusesWhenNoLongerCaughtUp() {
		MonotoneCell<Integer, String> cell = new MonotoneCell<>(0);
		cell.grow(v -> Option.of(v + 1));

		// the subscriber believes the value is still 0 — it must keep reading
		assertThat(cell.park("stale", v -> v == 0)).isFalse();
		assertThat(cell.parkedCount()).isEqualTo(0);
	}

	@Test
	public void drainParkedHarvestsEveryone() {
		MonotoneCell<Integer, String> cell = new MonotoneCell<>(0);
		cell.park("a", v -> true);
		cell.park("b", v -> true);

		assertThat(cell.drainParked()).containsExactly("a", "b");
		assertThat(cell.parkedCount()).isEqualTo(0);
		assertThat(cell.drainParked()).isEmpty();
	}
}
