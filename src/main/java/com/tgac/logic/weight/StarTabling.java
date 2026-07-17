package com.tgac.logic.weight;

// ABOUTME: Solves a sealed dependency closure: index its nodes, read base/edge off
// ABOUTME: the graph into one matrix over all its answers, run the Kleene star.

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.unification.Reified;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a sealed closure's slice of the {@link DependencyGraph} into the answer
 * values {@code x = A* ⊗ b} (docs/design/star-tabling.md §4.4). A closure is a
 * whole SCC — one entry (self-loop, left recursion) or several (mutual recursion)
 * plus any already-solved entries it references as constants — that seal together.
 * Every member's answers share one global index, so a cross-entry edge places
 * into the joint matrix and the coupling is solved once. Pure — no fibers.
 */
final class StarTabling {

	private StarTabling() {
	}

	static Map<TableEntry<Object>, Map<Reified<?>, SemiringStore>> solveGroup(
			Collection<TableEntry<Object>> entries, DependencyGraph graph, ClosedSemiring<SemiringStore> ring) {
		// one global index over every (entry, answer) node in the closure
		Map<Node, Integer> index = new LinkedHashMap<>();
		List<Node> nodes = new ArrayList<>();
		for (TableEntry<Object> entry : entries) {
			for (int i = 0; i < entry.getAnswerCount(); i++) {
				Node node = new Node(entry, entry.getAnswerAt(i)._1);
				index.put(node, nodes.size());
				nodes.add(node);
			}
		}
		int n = nodes.size();

		SemiringStore[] b = new SemiringStore[n];
		SemiringStore[][] a = new SemiringStore[n][n];
		for (int i = 0; i < n; i++) {
			b[i] = graph.base(nodes.get(i));
			Arrays.fill(a[i], ring.zero());
		}
		for (Edge edge : graph.edges()) {
			Integer from = index.get(edge.getFrom());
			Integer to = index.get(edge.getTo());
			if (from != null && to != null) {
				a[from][to] = ring.plus(a[from][to], graph.coefficient(edge));
			}
		}

		SemiringStore[] x = StarSolve.solve(ring, a, b);
		Map<TableEntry<Object>, Map<Reified<?>, SemiringStore>> solved = new LinkedHashMap<>();
		for (int idx = 0; idx < n; idx++) {
			Node node = nodes.get(idx);
			solved.computeIfAbsent(node.getEntry(), e -> new LinkedHashMap<>()).put(node.getAnswer(), x[idx]);
		}
		return solved;
	}
}
