package com.tgac.logic.constraints.store;

// ABOUTME: Both namespaces at once, minting fresh vars for unlisted names —
// ABOUTME: mints are recorded, so a shared instance keeps a local ONE variable.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Hole;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Both namespaces at once, minting a fresh var for every unlisted name —
 * the existential: mints are RECORDED, so occurrences across one delivery
 * (and across stores sharing this instance) become one variable.
 */
final class Minting implements Renaming {

	private final Map<Term<?>, Term<?>> targets;

	Minting(Map<? extends Term<?>, Term<?>> seed) {
		this.targets = new java.util.HashMap<>(seed);
	}

	@Override
	public Fiber<Term<?>> apply(Term<?> term) {
		Set<Term<?>> names = namesIn(term);
		if (names.isEmpty()) {
			return Fiber.done(term);
		}
		names.forEach(name -> targets.computeIfAbsent(name, miss -> LVar.lvar()));
		HashMap<LVar<?>, Term<?>> varTargets = names.stream()
				.flatMap(name -> name.asVar().toJavaStream())
				// an identity entry means "keep" — walk's chain-follower
				// must never see a self-binding
				.filter(name -> !name.equals(targets.get(name)))
				.collect(HashMap.collector(name -> name, targets::get));
		int maxSlot = names.stream()
				.filter(name -> !name.asVar().isDefined())
				.map(Hole.class::cast)
				.mapToInt(Hole::getNumber)
				.max().orElse(-1);
		Fiber<Term<?>> renamedVars = varTargets.isEmpty()
				? Fiber.done(term)
				: MiniKanren.walkAll(Substitutions.of(varTargets), term).map(t -> t);
		if (maxSlot < 0) {
			return renamedVars;
		}
		List<Term<?>> bySlot = IntStream.rangeClosed(0, maxSlot)
				.<Term<?>> mapToObj(i -> targets.getOrDefault(Hole.of(i), Hole.of(i)))
				.collect(Collectors.toList());
		return renamedVars.flatMap(r -> MiniKanren.instantiate(r, bySlot).map(t -> t));
	}

	private static boolean isName(Term<?> t) {
		return t.asVar().isDefined() || t.asReified().isDefined();
	}

	/** Iterative structural scan — deep spines must not recurse. */
	private static Set<Term<?>> namesIn(Term<?> t) {
		Set<Term<?>> names = new LinkedHashSet<>();
		Deque<Term<?>> work = new ArrayDeque<>();
		work.push(t);
		while (!work.isEmpty()) {
			Term<?> current = work.pop();
			if (isName(current)) {
				names.add(current);
			} else {
				MiniKanren.members(current).forEach(members -> members.forEach(work::push));
			}
		}
		return names;
	}
}
