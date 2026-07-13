package com.tgac.logic.tabling;

// ABOUTME: A broadcast channel with replay: answers append once (alpha-dedup),
// ABOUTME: late subscribers read from their index, appending wakes the parked.

import com.tgac.logic.unification.Reified;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;

/**
 * The answer half of a table entry, as the concurrency primitive it is.
 * Invariants, all under this monitor:
 * <ul>
 * <li>answers only grow, and only by values not already present — reified
 * terms carry alpha-equivalence as plain equality, so {@code contains} is
 * the strict-ascent guard: no growth, no wake;</li>
 * <li>a successful {@link #append} drains ALL parked subscribers (they
 * resume from their saved index and will see the new answer);</li>
 * <li>{@link #park} refuses when answers arrived past the subscriber's
 * index — it should keep reading instead (the park/append race).</li>
 * </ul>
 */
final class AnswerLog {

	private final ArrayList<Reified<?>> answers = new ArrayList<>();
	private final ArrayList<Registration> parked = new ArrayList<>();

	/** @return the drained subscribers to respawn, or none if the answer is a duplicate */
	synchronized Option<List<Registration>> append(Reified<?> answerTerm) {
		if (answers.contains(answerTerm)) {
			return Option.none();
		}
		answers.add(answerTerm);
		List<Registration> drained = List.ofAll(parked);
		parked.clear();
		return Option.of(drained);
	}

	/** @return false if answers arrived past the subscriber's index — keep reading */
	synchronized boolean park(Registration registration) {
		if (registration.getNextIndex() < answers.size()) {
			return false;
		}
		parked.add(registration);
		return true;
	}

	synchronized Reified<?> answerAt(int index) {
		return index < answers.size() ? answers.get(index) : null;
	}

	synchronized int answerCount() {
		return answers.size();
	}

	synchronized int parkedCount() {
		return parked.size();
	}

	/** Harvest every parked subscriber — the entry completed; they are dead. */
	synchronized List<Registration> drainParked() {
		List<Registration> dead = List.ofAll(parked);
		parked.clear();
		return dead;
	}
}
