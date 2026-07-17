package com.tgac.logic.weight;

// ABOUTME: Solves a sealed closed-tabling group jointly: gather every member entry's
// ABOUTME: base/edges into one matrix over all their answers, run the Kleene star.

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.unification.Reified;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a sealed group's captured base and edge maps (§4.1) into the answer
 * values {@code x = A* ⊗ b} (docs/design/star-tabling.md §4.4). A group is a whole
 * SCC — one entry (self-loop, left recursion) or several (mutual recursion) that
 * seal together. Every member's answers share one global index, so a cross-entry
 * edge {@code a's answer ← b's answer} places into the joint matrix and the
 * coupling is solved once. Pure — no fibers, no scheduler.
 */
public final class StarTabling {

	private StarTabling() {
	}

	/** Convenience for a one-entry group (self-loop / left recursion). */
	public static <S> Map<Reified<?>, S> solve(TableEntry<?> entry, ClosedSemiring<S> ring) {
		return solveGroup(Collections.singletonList(entry), ring).get(entry);
	}

	public static <S> Map<TableEntry<?>, Map<Reified<?>, S>> solveGroup(
			List<TableEntry<?>> entries, ClosedSemiring<S> ring) {
		// one global index over every (entry, answer) in the group
		Map<Tuple2<TableEntry<?>, Reified<?>>, Integer> index = new LinkedHashMap<>();
		List<Tuple2<TableEntry<?>, Reified<?>>> keys = new ArrayList<>();
		for (TableEntry<?> entry : entries) {
			for (int i = 0; i < entry.getAnswerCount(); i++) {
				Tuple2<TableEntry<?>, Reified<?>> key = Tuple.of(entry, entry.getAnswerAt(i)._1);
				index.put(key, keys.size());
				keys.add(key);
			}
		}
		int n = keys.size();

		Array<S> b = Array.empty();
		for (Tuple2<TableEntry<?>, Reified<?>> key : keys) {
			b = b.append(value(key._1.baseWeights().get(key._2), ring));
		}

		Object[][] a = new Object[n][n];
		for (Object[] row : a) {
			java.util.Arrays.fill(row, ring.zero());
		}
		for (TableEntry<?> entry : entries) {
			for (Map.Entry<Tuple3<Reified<?>, TableEntry<Object>, Reified<?>>, Object> edge : entry.edges().entrySet()) {
				Integer i = index.get(Tuple.of(entry, edge.getKey()._1));
				Integer j = index.get(Tuple.of((TableEntry<?>) edge.getKey()._2, edge.getKey()._3));
				if (i != null && j != null) {
					a[i][j] = ring.plus(cast(a[i][j]), cast(edge.getValue()));
				}
			}
		}

		Array<Array<S>> matrix = Array.empty();
		for (int i = 0; i < n; i++) {
			Array<S> row = Array.empty();
			for (int j = 0; j < n; j++) {
				row = row.append(cast(a[i][j]));
			}
			matrix = matrix.append(row);
		}

		Array<S> x = StarSolve.solve(ring, matrix, b);
		Map<TableEntry<?>, Map<Reified<?>, S>> solved = new LinkedHashMap<>();
		for (int idx = 0; idx < n; idx++) {
			Tuple2<TableEntry<?>, Reified<?>> key = keys.get(idx);
			solved.computeIfAbsent(key._1, e -> new LinkedHashMap<>()).put(key._2, x.get(idx));
		}
		return solved;
	}

	@SuppressWarnings("unchecked")
	private static <S> S value(Object captured, ClosedSemiring<S> ring) {
		return captured == null ? ring.zero() : (S) captured;
	}

	@SuppressWarnings("unchecked")
	private static <S> S cast(Object o) {
		return (S) o;
	}
}
