package com.tgac.logic.tabling.subsumption;

// ABOUTME: Herbrand pattern subsumption over reified terms: one-way instance
// ABOUTME: matching, anys binding consistently — the retrieval's precision layer.

import com.tgac.logic.unification.Any;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Herbrand subsumption: does {@code general} generalize {@code specific}?
 * One-way instance MATCHING (no unification, no anti-unification): the
 * general pattern's anys bind consistently to specific's subterms —
 * repeated anys demand equal subterms, which reified canonical names make
 * decidable by plain equality.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Subsumption {

	/**
	 * Iterative on an explicit worklist: reified structures nest linearly
	 * (an LList's spine is one frame per element), so a recursive walk
	 * overflows on long list arguments — depth goes to the heap instead.
	 */
	public static boolean subsumes(Term<?> general, Term<?> specific) {
		Map<Integer, Term<?>> binding = new HashMap<>();
		ArrayDeque<Tuple2<Term<?>, Term<?>>> pending = new ArrayDeque<>();
		pending.push(Tuple.of(general, specific));
		while (!pending.isEmpty()) {
			Tuple2<Term<?>, Term<?>> pair = pending.pop();
			Term<?> g = pair._1;
			Term<?> s = pair._2;
			if (g instanceof Any) {
				int any = ((Any<?>) g).getNumber();
				Term<?> bound = binding.get(any);
				if (bound == null) {
					binding.put(any, s);
				} else if (!bound.equals(s)) {
					return false;
				}
				continue;
			}
			if (s instanceof Any) {
				// a concrete general position cannot cover the any's instances
				return false;
			}
			Option<Iterable<Term<?>>> gm = MiniKanren.members(g);
			Option<Iterable<Term<?>>> sm = MiniKanren.members(s);
			if (gm.isEmpty() && sm.isEmpty()) {
				if (!g.equals(s)) {
					return false;
				}
				continue;
			}
			if (gm.isEmpty() || sm.isEmpty()) {
				return false;
			}
			Iterator<Term<?>> gi = gm.get().iterator();
			Iterator<Term<?>> si = sm.get().iterator();
			while (gi.hasNext() && si.hasNext()) {
				pending.push(Tuple.of(gi.next(), si.next()));
			}
			if (gi.hasNext() || si.hasNext()) {
				return false;
			}
		}
		return true;
	}
}
