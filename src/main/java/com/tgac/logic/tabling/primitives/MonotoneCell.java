package com.tgac.logic.tabling.primitives;

// ABOUTME: A cell whose value only grows, waking parked subscribers on growth —
// ABOUTME: monotone writes, threshold reads: the LVars shape, park-as-data style.

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A broadcast channel with replay, split along the value/subscriber seam:
 * the VALUE is a persistent element that only grows (the caller's step
 * decides what growth means — returning none refuses, the strict-ascent
 * guard); SUBSCRIBERS park as data and are drained wholesale by the next
 * growth. Invariants, all under this monitor:
 * <ul>
 * <li>a successful {@link #grow} swaps the value and drains ALL parked
 * subscribers — whoever grew the value wakes them;</li>
 * <li>{@link #park} refuses when the subscriber is no longer caught up —
 * the park/grow race resolves toward reading, never toward sleeping past
 * data;</li>
 * <li>{@link #drainParked} harvests everyone — for when the value is
 * declared final and sleepers are dead.</li>
 * </ul>
 */
public final class MonotoneCell<V, S> {

	private V value;
	private final ArrayList<S> parked = new ArrayList<>();

	public MonotoneCell(V initial) {
		this.value = initial;
	}

	/** A consistent snapshot; the value is persistent, so read it lock-free after. */
	public synchronized V read() {
		return value;
	}

	/** @return the drained subscribers to wake, or none when the step refused */
	public synchronized Option<List<S>> grow(Function<V, Option<V>> step) {
		Option<V> grown = step.apply(value);
		if (grown.isEmpty()) {
			return Option.none();
		}
		value = grown.get();
		List<S> drained = List.ofAll(parked);
		parked.clear();
		return Option.of(drained);
	}

	/** @return false if the value moved past the subscriber — keep reading instead */
	public synchronized boolean park(S subscriber, Predicate<V> caughtUp) {
		if (!caughtUp.test(value)) {
			return false;
		}
		parked.add(subscriber);
		return true;
	}

	public synchronized List<S> drainParked() {
		List<S> dead = List.ofAll(parked);
		parked.clear();
		return dead;
	}

	public synchronized int parkedCount() {
		return parked.size();
	}
}
