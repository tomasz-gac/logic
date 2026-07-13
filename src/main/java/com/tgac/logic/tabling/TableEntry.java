package com.tgac.logic.tabling;

// ABOUTME: Holds the cached answers and parked consumer continuations for one tabled call.
// ABOUTME: Consumers never block: they park here as data and are respawned by addAnswer.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

	/** Answer terms (reified argument tuples) in production order. Guarded by this. */
	private final ArrayList<Reified<?>> answers = new ArrayList<>();

	/** Consumers waiting for answers past the end of the cache. Guarded by this. */
	private final ArrayList<Registration> registrations = new ArrayList<>();

	/** Whether a master has claimed this call */
	private final AtomicBoolean masterActive = new AtomicBoolean(false);

	/**
	 * KEYS-FINAL: no new answer bindings will ever arrive
	 * (docs/design/table-completion.md §5). Upward-closed — once set, forever
	 * set — so racy reads are sound: a stale false prices ∞, a true is
	 * permanent. Flipped by {@link #tryCompleteHere()} when this entry's
	 * production drains; manual marking remains for tests.
	 */
	private final AtomicBoolean complete = new AtomicBoolean(false);

	/**
	 * Dijkstra–Scholten as two monotone counters: production work units
	 * started (the master, each respawned consumer) and finished (their
	 * fibers completing). Guarded by this — no read-ordering subtleties.
	 */
	private long spawned;
	private long finished;

	/**
	 * Registrations created DURING THIS ENTRY'S PRODUCTION, parked elsewhere:
	 * latent work that could still derive answers here. Maps each to the entry
	 * it parks on. Guarded by this.
	 */
	private final Map<Registration, TableEntry> outposts = new HashMap<>();

	public void markComplete() {
		complete.set(true);
	}

	public boolean isComplete() {
		return complete.get();
	}

	synchronized void workStarted() {
		spawned++;
	}

	synchronized void workFinished() {
		finished++;
	}

	synchronized void addOutpost(Registration r, TableEntry parkedOn) {
		outposts.put(r, parkedOn);
	}

	synchronized void removeOutpost(Registration r) {
		outposts.remove(r);
	}

	public synchronized int registrationCount() {
		return registrations.size();
	}

	/**
	 * The self-SCC completion rule (docs/design/table-completion.md §4):
	 * counters drained AND every outpost parks here or on an already-complete
	 * entry. Returns the registrations that were parked HERE when the flag
	 * flipped — provably dead, handed to the caller for the completion
	 * cascade — or null when the rule does not fire. Foreign flags are read
	 * without their locks (upward-closed: a stale false only defers).
	 */
	synchronized List<Registration> tryCompleteHere() {
		if (complete.get() || spawned == 0 || finished != spawned) {
			return null;
		}
		for (Map.Entry<Registration, TableEntry> o : outposts.entrySet()) {
			if (o.getValue() != this && !o.getValue().isComplete()) {
				return null;
			}
		}
		complete.set(true);
		List<Registration> dead = List.ofAll(registrations);
		registrations.clear();
		return dead;
	}

	/**
	 * A consumer parked as data: its continuation, the state it was consuming
	 * in, the arguments it unifies answers against, the cache index it will
	 * resume from, and THE ENTRY WHOSE PRODUCTION IT CONTINUES (null at top
	 * level) — where it parks says what it waits for; this says who it works
	 * for, resolved once from the parked package's Producer tag.
	 */
	@Value
	public static class Registration {
		Fiber.Fn<Package, Nothing> continuation;
		Package pkg;
		Unifiable<?> argsTerm;
		int nextIndex;
		TableEntry producer;
	}

	public TableEntry(Call call) {
		this.call = call;
	}

	/**
	 * Try to become the master for this table entry.
	 * Returns true if this caller became the master, false if another master exists.
	 */
	public boolean tryBecomeMaster() {
		if (masterActive.compareAndSet(false, true)) {
			workStarted();
			return true;
		}
		return false;
	}

	/**
	 * Cache an answer term unless an alpha-equivalent one is already present:
	 * answers are reified, and reified vars carry value equality by canonical
	 * name, so plain equality decides alpha-equivalence.
	 *
	 * @return the drained registrations to respawn, or none if the answer is a duplicate
	 */
	public synchronized Option<List<Registration>> addAnswer(Reified<?> answerTerm) {
		if (answers.contains(answerTerm)) {
			return Option.none();
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
	public synchronized Reified<?> getAnswerAt(int index) {
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

	@Override
	public String toString() {
		return "TableEntry{" +
				"call=" + call +
				", answers=" + getAnswerCount() +
				", registrations=" + getRegistrationCount() +
				'}';
	}
}
