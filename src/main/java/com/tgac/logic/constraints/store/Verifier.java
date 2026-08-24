package com.tgac.logic.constraints.store;

// ABOUTME: The marker of a trial-based family: it verifies the OTHERS' knowledge,
// ABOUTME: so the driver folds it after every value family has reacted.

/**
 * A family that verifies its claims by TRIAL against the rest of the
 * package's knowledge (nogoods: sequential scratch imposition read three
 * ways — fail = refuted, unchanged = crossed off, new = owed). The
 * crossed-off reading is package EQUALITY, so verification presupposes a
 * base where every value family has finished reacting to the current
 * trigger — a claim about knowledge can only be judged against knowledge
 * in normal form. The driver honors the presupposition structurally: the
 * revision fold visits marked families AFTER every unmarked one, so a
 * trial never samples a mid-trigger un-revised base. Queued work is the
 * presupposition's other half and stays {@code Propagation.settled}'s
 * business.
 *
 * <p>Verifiers are mutually UNORDERED: a second trial-based family would
 * sample this one's un-revised state. One exists today; ordering among
 * several is nogood-store doc territory when it arrives.
 */
public interface Verifier {
}
