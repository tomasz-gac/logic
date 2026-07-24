package com.tgac.logic.constraints.store;

// ABOUTME: A variable renaming applied to constraint knowledge: walk through the
// ABOUTME: home substitutions, then map — a miss keeps its name or mints fresh (∃).

import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The one operation knowledge needs to cross a boundary: change variable
 * names. {@link #walking} normalizes — resolve every var through the home
 * substitutions, keep the names of what stays free (spent entries fall to
 * values and drop store-side). {@link #into} retargets — seeded vars go to
 * their targets, and an UNSEEDED var mints a fresh one, recorded, so later
 * lookups (this store or another) agree: one Renaming shared across a
 * delivery is what keeps a local shared between stores one variable. The
 * mint is the existential: fresh per renaming, never reused across
 * deliveries.
 */
public final class Renaming {

	private final Substitutions home;
	private final Map<LVar<?>, Term<?>> targets;
	private final boolean mintOnMiss;

	private Renaming(Substitutions home, Map<LVar<?>, Term<?>> targets, boolean mintOnMiss) {
		this.home = home;
		this.targets = targets;
		this.mintOnMiss = mintOnMiss;
	}

	/** Normalization: walk through {@code home}, free vars keep their names. */
	public static Renaming walking(Substitutions home) {
		return new Renaming(home, new java.util.HashMap<>(), false);
	}

	/** Retargeting: {@code seed} maps vars to targets; misses mint fresh vars. */
	public static Renaming into(Map<LVar<?>, Term<?>> seed) {
		return new Renaming(null, seed, true);
	}

	/** The term under this renaming — deep: walked, then every var mapped. */
	public Term<?> apply(Term<?> term) {
		Term<?> walked = home == null ? term : MiniKanren.walkAll(home, term).get();
		Set<LVar<?>> vars = varsOf(walked);
		if (vars.isEmpty()) {
			return walked;
		}
		if (vars.size() == 1 && walked.asVar().isDefined()) {
			return target((LVar<?>) walked.asVar().get());
		}
		HashMap<LVar<?>, Term<?>> substitution = HashMap.empty();
		for (LVar<?> v : vars) {
			substitution = substitution.put(v, target(v));
		}
		return MiniKanren.walkAll(Substitutions.of(substitution), walked).get();
	}

	private Term<?> target(LVar<?> v) {
		Term<?> known = targets.get(v);
		if (known != null) {
			return known;
		}
		if (!mintOnMiss) {
			return v;
		}
		Term<?> fresh = LVar.lvar();
		targets.put(v, fresh);
		return fresh;
	}

	/** Iterative structural scan — deep spines must not recurse. */
	private static Set<LVar<?>> varsOf(Term<?> t) {
		Set<LVar<?>> vars = new LinkedHashSet<>();
		Deque<Term<?>> work = new ArrayDeque<>();
		work.push(t);
		while (!work.isEmpty()) {
			Term<?> current = work.pop();
			if (current.asVar().isDefined()) {
				vars.add((LVar<?>) current.asVar().get());
			} else {
				MiniKanren.members(current).forEach(members -> members.forEach(work::push));
			}
		}
		return vars;
	}
}
