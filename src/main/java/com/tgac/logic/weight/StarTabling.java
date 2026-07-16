package com.tgac.logic.weight;

// ABOUTME: Solves a sealed closed-tabling entry: gather the captured A/b off the
// ABOUTME: entry, run the Kleene star, return each answer's value x = A* ⊗ b.

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a sealed entry's captured base and edge maps (§4.1) into the answer
 * values `x = A* ⊗ b` (docs/design/star-tabling.md §4.4). Pure — no fibers, no
 * scheduler: the coefficients and bases are already on the entry, indexed by the
 * cell's answer keys. Emit is a separate step; this only computes the values.
 */
public final class StarTabling {

	private StarTabling() {
	}

	public static <S> Map<Reified<?>, S> solve(TableEntry<?> entry, ClosedSemiring<S> ring) {
		int n = entry.getAnswerCount();
		List<Reified<?>> answers = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			answers.add(entry.getAnswerAt(i)._1);
		}
		Map<Reified<?>, Object> bases = entry.baseWeights();
		Map<Tuple2<Reified<?>, Reified<?>>, Object> edges = entry.edges();

		Array<S> b = Array.empty();
		for (int i = 0; i < n; i++) {
			b = b.append(value(bases.get(answers.get(i)), ring));
		}
		Array<Array<S>> a = Array.empty();
		for (int i = 0; i < n; i++) {
			Array<S> row = Array.empty();
			for (int j = 0; j < n; j++) {
				row = row.append(value(edges.get(Tuple.of(answers.get(i), answers.get(j))), ring));
			}
			a = a.append(row);
		}

		Array<S> x = StarSolve.solve(ring, a, b);
		Map<Reified<?>, S> solved = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			solved.put(answers.get(i), x.get(i));
		}
		return solved;
	}

	@SuppressWarnings("unchecked")
	private static <S> S value(Object captured, ClosedSemiring<S> ring) {
		return captured == null ? ring.zero() : (S) captured;
	}
}
