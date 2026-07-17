package com.tgac.logic.weight;

// ABOUTME: A directed edge of the star equation graph: from depends on to
// ABOUTME: (from's value references to's, weighted by a captured coefficient).

import lombok.Value;

/**
 * A dependency in the equation system: {@code from}'s equation references
 * {@code to}'s value — the term {@code A[from][to] ⊗ x_to} in {@code from}'s row.
 * Born when a derivation of {@code from} consumed {@code to} while it was open.
 */
@Value
public class Edge {
	Node from;
	Node to;
}
