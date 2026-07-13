package com.tgac.logic.tabling.primitives;

// ABOUTME: A sealable region of work: a growing value, the work producing it,
// ABOUTME: the seal, and the cascade — termination detection as one value.

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The termination-detection unit: a {@link MonotoneCell} (the region's
 * published value, growing monotonically, waking parked subscribers) paired
 * with a {@link WorkLedger} (everything working for the region — running
 * fibers and sleeping subscribers, each recorded with the region it sleeps
 * at) and a SEAL — the upward-closed, CAS'd-once declaration that the value
 * is final. Racy seal reads are sound: a stale false only defers.
 *
 * <p>The one domain-specific input is {@code ownerOf}: given a subscriber,
 * which region's work is it (null = unowned top-level work, unbilled,
 * gating nothing). Everything else is the theorem:
 *
 * <p>{@link #track} is the billing door — every unit of the region's work
 * passes through exactly once (start ticks at wrap time, no gap for a
 * racing seal; finish at fiber end, then a cascade attempt).
 *
 * <p>The SEAL RULE (internal): counters drained and every sleeper parked
 * HOME (waking needs new growth here, which needs running work here — just
 * ruled out) or at an already-sealed region (never grows again). Then flag
 * CAS, then the parked subscribers are provably dead.
 *
 * <p>{@link #sealCascade} propagates seals backwards along sleeper edges:
 * sealing kills the sleepers parked here; each dead sleeper's owner loses
 * an obstruction and is rechecked. Monitors never nest — each region's
 * rule runs under its own locks, the walk happens outside them.
 */
public final class Region<V, S> {

	private final MonotoneCell<V, S> cell;
	private final WorkLedger<S, Region<V, S>> ledger = new WorkLedger<>();
	private final AtomicBoolean sealed = new AtomicBoolean(false);
	private final Function<S, Region<V, S>> ownerOf;

	public Region(V initial, Function<S, Region<V, S>> ownerOf) {
		this.cell = new MonotoneCell<>(initial);
		this.ownerOf = ownerOf;
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

	/**
	 * Bill {@code work} as one unit of this region's running work, with a
	 * cascade attempt on finish. Null-tolerant statically: unowned work
	 * runs unbilled.
	 */
	public static <V, S> Fiber<Nothing> track(Region<V, S> region, Fiber<Nothing> work) {
		if (region == null) {
			return work;
		}
		return region.ledger.counted(work, region::sealCascade);
	}

	public void sleeping(S sleeper, Region<V, S> at) {
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

	/** Seal this region if quiescent and propagate along sleeper edges. */
	public void sealCascade() {
		ArrayDeque<Region<V, S>> queue = new ArrayDeque<>();
		queue.add(this);
		while (!queue.isEmpty()) {
			List<S> dead = queue.poll().sealIfQuiescent();
			if (dead == null) {
				continue;
			}
			for (S sleeper : dead) {
				Region<V, S> owner = ownerOf.apply(sleeper);
				if (owner != null) {
					owner.awake(sleeper);
					queue.add(owner);
				}
			}
		}
	}

	private List<S> sealIfQuiescent() {
		if (!ledger.quiescent(at -> at == this || at.isSealed())) {
			return null;
		}
		if (!sealed.compareAndSet(false, true)) {
			return null;
		}
		return cell.drainParked();
	}
}
