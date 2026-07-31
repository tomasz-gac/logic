package com.tgac.logic.tabling;

// ABOUTME: One consumer's reading state: continuation, call-site package, args
// ABOUTME: and cursor - carried by the live consuming frame, never stored.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import com.tgac.logic.unification.Unifiable;
import lombok.Value;

/**
 * A reader: the consumer's continuation, the state it consumes in, the
 * arguments it unifies answers against, and the cache index it reads next.
 * A parameter bundle for the LIVE consuming frame — when the reader catches
 * up, the frame itself parks at the entry's channel (Fiber.await) and this
 * object simply rides its captured state; nothing stores it. The channel it
 * parks at says what it waits for; the frame's ambient scope says whose
 * ledger pays for its work.
 */
@Value
public class Reader {
	Fiber.Fn<Package, Nothing> continuation;
	Package pkg;
	Unifiable<?> argsTerm;
	int nextIndex;

	/**
	 * The partial-region answers this reader has delivered — membership, not
	 * an index, because an antichain ascent may evict what an index pointed
	 * at. Ground answers keep the cursor: append-only enumerations make a
	 * delivered-set redundant there.
	 */
	HashSet<AnswerKey> delivered;

	/**
	 * The ground values as delivered — a ⊕-fold may improve a cached key
	 * after this reader passed it (min-plus finding a cheaper cost), and
	 * downstream ⊕ absorbs re-delivery, so the improved fold is handed on.
	 * Presence values never move, so this map never disagrees there.
	 */
	HashMap<AnswerKey, Object> groundValues;

	/**
	 * The solve's table, reached through the caller's package — the shared
	 * transport store every branch of one solve names identically.
	 */
	public Table getTable() {
		return pkg.getStore(Table.class);
	}

	/** The reader at the call site: cursor at the start of the cache. */
	static Reader of(Fiber.Fn<Package, Nothing> continuation, Package pkg, Unifiable<?> argsTerm) {
		return new Reader(continuation, pkg, argsTerm, 0, HashSet.empty(), HashMap.empty());
	}

	/** The same reader, one atom further along, its delivered value recorded. */
	Reader advanced(AnswerKey key, Object value) {
		return new Reader(continuation, pkg, argsTerm, nextIndex + 1, delivered,
				groundValues.put(key, value));
	}

	/** The same reader after re-delivering {@code key}'s improved fold. */
	Reader redelivered(AnswerKey key, Object value) {
		return new Reader(continuation, pkg, argsTerm, nextIndex, delivered,
				groundValues.put(key, value));
	}

	/** The key's fold has moved past what this reader handed on. */
	boolean groundValueImproved(AnswerKey key, Object value) {
		return groundValues.get(key).map(seen -> !seen.equals(value)).getOrElse(false);
	}

	/** The same reader, one partial-region answer marked delivered. */
	Reader delivered(AnswerKey key) {
		return new Reader(continuation, pkg, argsTerm, nextIndex, delivered.add(key), groundValues);
	}

	boolean hasDelivered(AnswerKey key) {
		return delivered.contains(key);
	}
}
