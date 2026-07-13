package com.tgac.logic.tabling.primitives;

// ABOUTME: A sealable region of work: a growing value, the work producing it,
// ABOUTME: and the seal — "this value is final" — decided by quiescence.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The termination-detection unit: a {@link MonotoneCell} (the region's
 * published value, growing monotonically, waking parked subscribers) paired
 * with a {@link WorkLedger} (everything working for the region — running
 * fibers and sleeping subscribers) and a SEAL — the upward-closed,
 * CAS'd-once declaration that the value is final. Racy reads of the seal
 * are sound: a stale false only defers.
 *
 * <p>{@link #track} is the billing door: every unit of the region's work
 * passes through it exactly once (start ticks at wrap time — no gap for a
 * racing seal check — finish at fiber end, then the caller's hook, e.g. a
 * completion cascade).
 *
 * <p>{@link #sealIfQuiescent} is the seal rule: ledger quiescent (counters
 * drained, every sleeper parked where the caller's predicate says it cannot
 * wake) → flag CAS (arbitrates racing checks; after a true quiescent
 * snapshot no legal transition can start new work — waking a home sleeper
 * needs new growth here, which needs running work here) → the parked
 * subscribers are provably dead, drained and returned for the caller's
 * cascade.
 */
public final class Region<V, S, P> {

	private final MonotoneCell<V, S> cell;
	private final WorkLedger<S, P> ledger = new WorkLedger<>();
	private final AtomicBoolean sealed = new AtomicBoolean(false);

	public Region(V initial) {
		this.cell = new MonotoneCell<>(initial);
	}

	// ---- the value half ----

	public V read() {
		return cell.read();
	}

	/** @return the drained subscribers to wake, or none when the step refused */
	public Option<List<S>> grow(Function<V, Option<V>> step) {
		return cell.grow(step);
	}

	/** @return false if the value moved past the subscriber — keep reading */
	public boolean park(S subscriber, Predicate<V> caughtUp) {
		return cell.park(subscriber, caughtUp);
	}

	public int parkedCount() {
		return cell.parkedCount();
	}

	// ---- the work half ----

	/** Bill {@code work} as one unit of this region's running work. */
	public Fiber<Nothing> track(Fiber<Nothing> work, Runnable onFinished) {
		return ledger.counted(work, onFinished);
	}

	public void sleeping(S sleeper, P at) {
		ledger.sleeping(sleeper, at);
	}

	public void awake(S sleeper) {
		ledger.awake(sleeper);
	}

	// ---- the seal ----

	public boolean isSealed() {
		return sealed.get();
	}

	/** Manual seal — tests and external certificates. */
	public void seal() {
		sealed.set(true);
	}

	/**
	 * The seal rule. @return the dead subscribers for the caller's cascade,
	 * or null when the rule does not fire.
	 */
	public List<S> sealIfQuiescent(Predicate<P> cannotWake) {
		if (!ledger.quiescent(cannotWake)) {
			return null;
		}
		if (!sealed.compareAndSet(false, true)) {
			return null;
		}
		return cell.drainParked();
	}
}
