package com.tgac.logic.tabling;

// ABOUTME: Holds the cached answers and parked consumer continuations for one tabled call.
// ABOUTME: Consumers never block: they park here as data and are respawned by addAnswer.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Value;

/**
 * A table entry for a specific tabled goal call.
 *
 * The first invocation of a call becomes the MASTER and executes the goal,
 * caching each new answer term it derives. Subsequent invocations are
 * CONSUMERS that unify their arguments against the cached answers. A consumer
 * that exhausts the cache registers its continuation here and terminates;
 * {@link #addAnswer} hands the registrations back to whoever derived the
 * answer, which respawns them as independent fibers. At the fixpoint no new
 * answers appear, remaining registrations never fire, and the computation
 * simply drains.
 */
public class TableEntry {
	/** The call being tabled */
	@Getter
	private final Call call;

	/** Answer terms (reified argument vectors) in production order. Guarded by this. */
	private final ArrayList<List<Term<?>>> answers = new ArrayList<>();

	/** Consumers waiting for answers past the end of the cache. Guarded by this. */
	private final ArrayList<Registration> registrations = new ArrayList<>();

	/** Whether a master has claimed this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	/**
	 * A consumer parked as data: its continuation, the state it was consuming
	 * in, the arguments it unifies answers against, and the cache index it
	 * will resume from.
	 */
	@Value
	public static class Registration {
		Fiber.Fn<Package, Nothing> continuation;
		Package pkg;
		List<Unifiable> args;
		int nextIndex;
	}

	public TableEntry(Call call) {
		this.call = call;
	}

	/**
	 * Try to become the master for this table entry.
	 * Returns true if this caller became the master, false if another master exists.
	 */
	public boolean tryBecomeMaster() {
		return masterActive.compareAndSet(false, true);
	}

	/**
	 * Cache an answer term unless an alpha-equivalent one is already present.
	 *
	 * @return the drained registrations to respawn, or none if the answer is a duplicate
	 */
	public synchronized Option<List<Registration>> addAnswer(List<Term<?>> answerTerm) {
		for (List<Term<?>> cached : answers) {
			if (answersEqual(cached, answerTerm)) {
				return Option.none();
			}
		}
		answers.add(answerTerm);
		List<Registration> drained = List.ofAll(registrations);
		registrations.clear();
		return Option.of(drained);
	}

	/**
	 * Park a consumer waiting at the end of the cache.
	 *
	 * @return false if answers arrived since the consumer last looked — it should keep consuming instead
	 */
	public synchronized boolean register(Registration registration) {
		if (registration.getNextIndex() < answers.size()) {
			return false;
		}
		registrations.add(registration);
		return true;
	}

	/**
	 * Get an answer term at the specified index, or null if not present.
	 */
	public synchronized List<Term<?>> getAnswerAt(int index) {
		return index < answers.size() ? answers.get(index) : null;
	}

	/**
	 * Get the current number of cached answers.
	 */
	public synchronized int getAnswerCount() {
		return answers.size();
	}

	/**
	 * Get the current number of parked consumers.
	 */
	public synchronized int getRegistrationCount() {
		return registrations.size();
	}

	/**
	 * Answer terms are equal when they are alpha-equivalent: both are reified,
	 * and reified vars carry value equality by canonical name, so plain
	 * structural equality decides it.
	 */
	private static boolean answersEqual(List<Term<?>> a, List<Term<?>> b) {
		return a.equals(b);
	}

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				", registrations=" + getRegistrationCount() +
				'}';
	}
}
