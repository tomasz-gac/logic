package com.tgac.logic.weight;

// ABOUTME: The star equation graph, built during closed explore: each node's base
// ABOUTME: seed and each edge's coefficient, both ⊕-folded over the derivations.

import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.logic.tabling.TableEntry;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The equation system as a graph, relocated out of the tabling core into the
 * layer that owns it. Explore writes it (each produce is a base or an edge); the
 * seal reads it (the closure walk names a solve group, {@link StarTabling} turns
 * it into a matrix). Values ⊕-fold, so multiple derivations of the same node or
 * edge combine. Concurrent: many produce fibers write during explore.
 */
final class DependencyGraph {

	private final ClosedSemiring<SemiringStore> ring;
	private final Map<Node, SemiringStore> bases = new ConcurrentHashMap<>();
	private final Map<Edge, SemiringStore> coefficients = new ConcurrentHashMap<>();

	DependencyGraph(ClosedSemiring<SemiringStore> ring) {
		this.ring = ring;
	}

	/** A non-looping derivation of {@code node}: ⊕-fold its base seed. */
	void addBase(Node node, SemiringStore value) {
		bases.merge(node, value, ring::plus);
	}

	/** A one-loop derivation: ⊕-fold {@code edge}'s coefficient (from depends on to). */
	void addEdge(Edge edge, SemiringStore value) {
		coefficients.merge(edge, value, ring::plus);
	}

	SemiringStore base(Node node) {
		return bases.getOrDefault(node, ring.zero());
	}

	SemiringStore coefficient(Edge edge) {
		return coefficients.getOrDefault(edge, ring.zero());
	}

	Set<Edge> edges() {
		return coefficients.keySet();
	}

	/** Every entry {@code start}'s equation transitively depends on, itself included. */
	Set<TableEntry<Object>> dependencyClosure(TableEntry<Object> start) {
		Set<TableEntry<Object>> closure = new LinkedHashSet<>();
		ArrayDeque<TableEntry<Object>> frontier = new ArrayDeque<>();
		frontier.add(start);
		while (!frontier.isEmpty()) {
			TableEntry<Object> entry = frontier.poll();
			if (!closure.add(entry)) {
				continue;
			}
			for (Edge edge : coefficients.keySet()) {
				if (edge.getFrom().getEntry() == entry) {
					frontier.add(edge.getTo().getEntry());
				}
			}
		}
		return closure;
	}

	// ---- inspection (tests) ----

	Map<Node, SemiringStore> bases() {
		return bases;
	}

	Map<Edge, SemiringStore> coefficients() {
		return coefficients;
	}
}
