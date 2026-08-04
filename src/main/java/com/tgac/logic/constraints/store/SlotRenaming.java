package com.tgac.logic.constraints.store;

// ABOUTME: Slot holes to their positional targets — instantiate over the slot
// ABOUTME: list; unlisted slots keep their names, hole-free terms pass by identity.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import java.util.ArrayDeque;
import java.util.Deque;
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

	/** Deepest slot number occurring in the term, -1 when hole-free — iterative, deep spines must not recurse. */
	private static int maxSlotIn(Term<?> term) {
		int maxSlot = -1;
		Deque<Term<?>> work = new ArrayDeque<>();
		work.push(term);
		while (!work.isEmpty()) {
			Term<?> current = work.pop();
			if (current.asReified().isDefined()) {
				maxSlot = Math.max(maxSlot, ((Hole<?>) current).getNumber());
			} else {
				MiniKanren.members(current).forEach(members -> members.forEach(work::push));
			}
		}
		return maxSlot;
	}
}
