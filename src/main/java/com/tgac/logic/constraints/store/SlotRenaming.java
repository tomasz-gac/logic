package com.tgac.logic.constraints.store;

// ABOUTME: Slot holes to their positional targets — instantiate over the slot
// ABOUTME: list; unlisted slots keep their names, hole-free terms pass by identity.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;

/** Slot holes to their positional targets: {@code instantiate} over the slot list. */
@RequiredArgsConstructor
final class SlotRenaming implements Renaming {

	private final List<? extends Term<?>> slotTargets;

	@Override
	public Fiber<Term<?>> apply(Term<?> term) {
		int maxSlot = maxSlotIn(term);
		if (maxSlot < 0) {
			return Fiber.done(term);
		}
		// instantiate MINTS holes it has no entry for; an unlisted slot
		// must keep its name instead
		List<Term<?>> bySlot = IntStream.rangeClosed(0, maxSlot)
				.<Term<?>> mapToObj(i -> i < slotTargets.size() ? slotTargets.get(i) : Hole.of(i))
				.collect(Collectors.toList());
		return MiniKanren.instantiate(term, bySlot).map(t -> t);
	}

	/** Deepest slot number occurring in the term, -1 when hole-free. */
	private static int maxSlotIn(Term<?> term) {
		return Renaming.namesIn(term)
				.filter(name -> name.asReified().isDefined())
				.map(Hole.class::cast)
				.mapToInt(Hole::getNumber)
				.max().orElse(-1);
	}
}
