package com.tgac.logic.tabling;

// ABOUTME: The cache key of a tabled call: the relation's identity plus its reified arguments.
// ABOUTME: Identity keying means distinct relations never share a cache.

import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.ReifiedVar;
import com.tgac.logic.unification.Term;
import io.vavr.control.Option;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.Value;

/**
 * A call to a tabled relation with specific arguments.
 *
 * Two calls are equal when they apply the same relation (by identity) to
 * alpha-equivalent arguments — the argument tuple is reified, so plain
 * equality on it decides variance.
 */
@Value
public class Call {
	Tabled<?> relation;
	Reified<?> arguments;

	public static Call of(Tabled<?> relation, Reified<?> arguments) {
		return new Call(relation, arguments);
	}

	/**
	 * Herbrand call subsumption: does this pattern generalize {@code other}?
	 * One-way instance MATCHING (no unification, no anti-unification): this
	 * pattern's holes bind consistently to other's subterms — repeated holes
	 * demand equal subterms, which reified canonical names make decidable by
	 * plain equality. When it holds, every answer of {@code other} is among
	 * this call's answers (the subset property), so a SEALED entry for this
	 * call may serve {@code other} as a read-only relation.
	 */
	public boolean subsumes(Call other) {
		return relation == other.relation
				&& match(arguments, other.arguments, new HashMap<>());
	}

	private static boolean match(Term<?> general, Term<?> specific, Map<String, Term<?>> binding) {
		if (general instanceof ReifiedVar) {
			String hole = ((ReifiedVar<?>) general).getName();
			Term<?> bound = binding.get(hole);
			if (bound == null) {
				binding.put(hole, specific);
				return true;
			}
			return bound.equals(specific);
		}
		if (specific instanceof ReifiedVar) {
			// a concrete general position cannot cover the hole's instances
			return false;
		}
		Option<Iterable<Term<?>>> gm = MiniKanren.members(general);
		Option<Iterable<Term<?>>> sm = MiniKanren.members(specific);
		if (gm.isEmpty() && sm.isEmpty()) {
			return general.equals(specific);
		}
		if (gm.isEmpty() || sm.isEmpty()) {
			return false;
		}
		Iterator<Term<?>> gi = gm.get().iterator();
		Iterator<Term<?>> si = sm.get().iterator();
		while (gi.hasNext() && si.hasNext()) {
			if (!match(gi.next(), si.next(), binding)) {
				return false;
			}
		}
		return !gi.hasNext() && !si.hasNext();
	}

	@Override
	public String toString() {
		return relation + "" + arguments;
	}
}
