package com.tgac.logic.tabling.primitives;

// ABOUTME: Region seal-cascade tests: the group seal marks every member sealed
// ABOUTME: before any member's onSealed hook fires (SEALED implies SOLVABLE).

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class RegionTest {

	/**
	 * A sleeper ring group-seals as one unit: at the moment any member's
	 * onSealed hook runs, EVERY member of the group must already read as
	 * sealed. This is what lets a mode's first-announced hook solve the whole
	 * closure instead of stashing work for the last announcement.
	 */
	@Test
	public void groupSealMarksEveryMemberBeforeAnyHookFires() {
		Map<String, Region<Integer, String>> owners = new HashMap<>();
		Function<String, Region<Integer, String>> ownerOf = owners::get;
		Region<Integer, String> a = new Region<>(0, ownerOf);
		Region<Integer, String> b = new Region<>(0, ownerOf);

		List<Boolean> groupSealedAtHook = new ArrayList<>();
		a.onSealed(drained -> {
			groupSealedAtHook.add(a.isSealed() && b.isSealed());
			return done(nothing());
		});
		b.onSealed(drained -> {
			groupSealedAtHook.add(a.isSealed() && b.isSealed());
			return done(nothing());
		});

		// the ring: a's subscriber sleeps at b, b's subscriber sleeps at a
		owners.put("a-reader", a);
		a.sleeping("a-reader", b);
		b.park("a-reader", v -> true);
		owners.put("b-reader", b);
		b.sleeping("b-reader", a);
		a.park("b-reader", v -> true);

		// each region runs one unit of tracked work (its master); the last
		// finish's cascade attempt finds the drained ring and group-seals it
		Region.track(a, Fiber.defer(() -> done(nothing()))).get();
		Region.track(b, Fiber.defer(() -> done(nothing()))).get();

		assertThat(a.isSealed()).isTrue();
		assertThat(b.isSealed()).isTrue();
		assertThat(groupSealedAtHook).containsExactly(true, true);
	}
}
