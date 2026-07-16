package com.tgac.logic.tabling.primitives;

// ABOUTME: A production's work ledger: the running half as two monotone counters,
// ABOUTME: the sleeping half as who-sleeps-where — quiescence is both halves empty.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Termination detection for one region of work whose pieces are either
 * RUNNING (live fibers — counted by the Dijkstra–Scholten pair
 * {@code started}/{@code finished}, both monotone), SLEEPING (parked at
 * some place {@code P}, wakeable), or dead (silence). {@link #quiescent}
 * is both halves reading empty: all fibers ended and every sleeper parks
 * where the caller's predicate says it can never wake.
 *
 * <p>{@link #counted} is the ONE pairing discipline: the start ticks
 * synchronously at wrap time (no gap for a racing quiescence check), the
 * finish when the work's fiber ends, followed by the caller's hook. A
 * leaked pair never completes (sound, useless); a doubled one completes
 * early (unsound) — every unit of work must pass through here exactly once.
 *
 * <p>Counters and sleepers guarded by this monitor; the quiescence
 * predicate may read foreign state lock-free when that state is
 * upward-closed (a stale false only defers).
 */
public final class WorkLedger<S, P> {

	private long started;
	private long finished;

	/** Sleeping pieces of this region, mapped to the place each parks at. */
	private final Map<S, P> sleeping = new HashMap<>();

	public synchronized void taskStarted() {
		started++;
	}

	public synchronized void taskFinished() {
		finished++;
	}

	public synchronized void sleeping(S sleeper, P at) {
		sleeping.put(sleeper, at);
	}

	public synchronized void awake(S sleeper) {
		sleeping.remove(sleeper);
	}

	/** Monotone — two equal reads bracket a spawn-free interval. */
	public synchronized long startedCount() {
		return started;
	}

	/** Counters drained: the region has run and all its fibers ended. */
	public synchronized boolean drained() {
		return started > 0 && finished == started;
	}

	/** The places this region's sleepers park at — a snapshot. */
	public synchronized List<P> sleepingAt() {
		return new ArrayList<>(sleeping.values());
	}

	public synchronized boolean quiescent(Predicate<P> cannotWake) {
		if (started == 0 || finished != started) {
			return false;
		}
		for (P at : sleeping.values()) {
			if (!cannotWake.test(at)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Count {@code work} as one unit of this ledger's running work. When the
	 * work's fiber ends, {@code onFinished} runs (the seal attempt) and the fiber
	 * it returns becomes this fiber's tail — so any work a seal spawns (the star
	 * emit) is stepped by the same scheduler drive.
	 */
	public Fiber<Nothing> counted(Fiber<Nothing> work, Supplier<Fiber<Nothing>> onFinished) {
		taskStarted();
		return work.flatMap(__ -> {
			taskFinished();
			return onFinished.get();
		});
	}
}
