package com.tgac.logic.weight;

// ABOUTME: A vertex of the star equation graph — one answer of one tabled call.
// ABOUTME: Entry AND answer, because two calls can reify to the same answer term.

import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.unification.Reified;
import lombok.Value;

/**
 * One unknown in the equation system: {@code x_(entry, answer)}. The answer term
 * is unique only within its entry (the cell dedups by term), but the graph spans
 * a whole dependency closure of entries, and two calls can reify to the same term
 * (`a(1)` and `b(1)` both to `Tuple1(1)`) — so a global vertex names its entry.
 */
@Value
public class Node {
	TableEntry<Object> entry;
	Reified<?> answer;
}
