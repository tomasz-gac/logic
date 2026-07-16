package com.tgac.logic.tabling.primitives;

// ABOUTME: A sealable region of work: a growing value, the work producing it,
// ABOUTME: the seal, and the cascade — termination detection as one value.

import static com.tgac.functional.category.Nothing.nothing;
import static com.tgac.functional.fibers.Fiber.done;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.Fiber;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

	/** Work to spawn the moment this region seals — the star emit. Inert by default. */
	private Supplier<Fiber<Nothing>> onSealed = () -> done(nothing());

	public Region(V initial, Function<S, Region<V, S>> ownerOf) {
		this.cell = new MonotoneCell<>(initial);
		this.ownerOf = ownerOf;
	}

	/** Register the fiber to spawn when this region seals (closed tabling's emit). */
	public void onSealed(Supplier<Fiber<Nothing>> work) {
		this.onSealed = work;
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

	/**
	 * Seal this region if quiescent and propagate along sleeper edges.
	 *
	 * @return the fiber composing every newly sealed region's {@link #onSealed}
	 * 		work (the star emit) — inert for plain tabling
	 */
	public Fiber<Nothing> sealCascade() {
		ArrayList<Fiber<Nothing>> emits = new ArrayList<>();
		ArrayDeque<Region<V, S>> queue = new ArrayDeque<>();
		queue.add(this);
		while (!queue.isEmpty()) {
			Region<V, S> region = queue.poll();
			List<S> dead = region.sealIfQuiescent(emits);
			if (dead == null) {
				// the singleton rule refused; if the region is drained and
				// unsealed, the obstruction is a foreign-unsealed sleeper —
				// try sealing its sleeper-closure as a group
				if (!region.isSealed() && region.ledger.drained()) {
					dead = groupSeal(region, emits);
				}
				if (dead == null) {
					continue;
				}
			}
			for (S sleeper : dead) {
				Region<V, S> owner = ownerOf.apply(sleeper);
				if (owner != null) {
					owner.awake(sleeper);
					queue.add(owner);
				}
			}
		}
		Fiber<Nothing> result = done(nothing());
		for (Fiber<Nothing> emit : emits) {
			Fiber<Nothing> tail = emit;
			result = result.flatMap(__ -> tail);
		}
		return result;
	}

	private List<S> sealIfQuiescent(ArrayList<Fiber<Nothing>> emits) {
		if (!ledger.quiescent(at -> at == this || at.isSealed())) {
			return null;
		}
		if (!sealed.compareAndSet(false, true)) {
			return null;
		}
		emits.add(onSealed.get());
		return cell.drainParked();
	}

	/**
	 * THE GROUP SEAL (Tier 2) — the singleton rule applied to a VIRTUAL
	 * MERGE (docs/design/group-seal.md). Define the merge of a set S of
	 * regions: ledger = sum of the members', sleepers = union, HOME =
	 * membership in S. The group condition is then the ordinary seal rule
	 * on merge(S), verbatim — merged ledger drained, every merged sleeper
	 * home or at a sealed region — and its soundness argument transfers
	 * with it: growth inside S needs running S-work (none), and nothing
	 * outside injects, because growth is billed to the grower's own region.
	 *
	 * <p>WHICH merge: the smallest one that makes all sleepers home — the
	 * walk below is a fixpoint ascent in the finite join-semilattice of
	 * region sets, closing {start} under sleeper-targets (a closure
	 * operator; running members abort the ascent, and their own finish
	 * events retry it).
	 *
	 * <p>The two-phase read is the price of evaluating the merged rule
	 * WITHOUT materializing a merged ledger: constituents keep their own
	 * monitors, and atomicity across them is reconstructed from the
	 * MONOTONE started counters — two equal reads bracket a spawn-free
	 * interval, a consistent snapshot with no nested monitors. Racing
	 * group seals are arbitrated per member by the flag CAS — a lost CAS
	 * just skips that member's drain. The merge exists for the duration of
	 * one rule-evaluation and is then discarded; eager permanent merging
	 * (SLG's ASCC) is the same algorithm with a different merge lifetime.
	 *
	 * @return the dead sleepers drained from every sealed member, or null
	 * 		when the group cannot seal yet
	 */
	private List<S> groupSeal(Region<V, S> start, ArrayList<Fiber<Nothing>> emits) {
		LinkedHashMap<Region<V, S>, Long> members = new LinkedHashMap<>();
		ArrayDeque<Region<V, S>> frontier = new ArrayDeque<>();
		frontier.add(start);
		while (!frontier.isEmpty()) {
			Region<V, S> region = frontier.poll();
			if (members.containsKey(region) || region.isSealed()) {
				continue;
			}
			if (!region.ledger.drained()) {
				return null;
			}
			members.put(region, region.ledger.startedCount());
			for (Region<V, S> at : region.ledger.sleepingAt()) {
				if (at != region && !at.isSealed()) {
					frontier.add(at);
				}
			}
		}
		for (Map.Entry<Region<V, S>, Long> m : members.entrySet()) {
			if (m.getKey().ledger.startedCount() != m.getValue()) {
				return null;
			}
		}
		List<S> dead = List.empty();
		for (Region<V, S> member : members.keySet()) {
			if (member.sealed.compareAndSet(false, true)) {
				emits.add(member.onSealed.get());
				dead = dead.appendAll(member.cell.drainParked());
			}
		}
		return dead;
	}
}
