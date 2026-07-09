package com.tgac.logic.unification;

// ABOUTME: The newly added bindings of one unification — mintable only by the unifier
// ABOUTME: and the checked single-binding constructor, so a prefix is born valid.

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;

/**
 * A delta of variable bindings (docs/design/constraint-kernel.md).
 * Construction is package-private: prefixes come from {@link MiniKanren}'s
 * unification (which walks before extending, so no pair targets a bound variable)
 * or from {@link #binding}, which checks. Consumers revalidate against the live
 * package at application time — a prefix may be applied later than it was minted.
 */
public final class Prefix {

	private final HashMap<LVar<?>, Term<?>> delta;

	Prefix(HashMap<LVar<?>, Term<?>> delta) {
		this.delta = delta;
	}

	/**
	 * A single inferred binding — none when {@code x} is already bound (walk it and
	 * unify instead; asserting over a bound variable is the silent-no-op trap).
	 */
	public static Option<Prefix> binding(Substitutions s, LVar<?> x, Term<?> value) {
		return s.walk(x) == x ?
				Option.of(new Prefix(HashMap.of(x, value))) :
				Option.none();
	}

	public boolean isEmpty() {
		return delta.isEmpty();
	}

	public Iterable<Tuple2<LVar<?>, Term<?>>> bindings() {
		return delta;
	}

	public HashMap<LVar<?>, Term<?>> toMap() {
		return delta;
	}

	/**
	 * The asserted reading of the prefix trichotomy against a live package: a pair
	 * for a still-open variable re-targets its walked representative; one bound to
	 * the same value is dropped; one bound to a DIFFERENT value is a contradiction
	 * — none. (Disequality's record verification reads the same trichotomy with the
	 * opposite polarity, over structural re-unification rather than equality — see
	 * the Step 3 consolidation note in the capability doc.)
	 */
	public Option<Prefix> revalidate(Substitutions s) {
		HashMap<LVar<?>, Term<?>> kept = HashMap.empty();
		for (Tuple2<LVar<?>, Term<?>> binding : delta) {
			Term<?> walked = s.walk(binding._1);
			if (walked.asVar().isDefined()) {
				kept = kept.put((LVar<?>) walked.asVar().get(), binding._2);
			} else if (!walked.equals(binding._2)) {
				return Option.none();
			}
		}
		return Option.of(new Prefix(kept));
	}

	/** The substitutions extended with this prefix. */
	public Substitutions appliedTo(Substitutions s) {
		Substitutions result = s;
		for (Tuple2<LVar<?>, Term<?>> binding : delta) {
			result = result.extend(binding._1, binding._2);
		}
		return result;
	}

	/** The substitution map extended with this prefix. */
	public HashMap<LVar<?>, Term<?>> appliedTo(HashMap<LVar<?>, Term<?>> substitutions) {
		HashMap<LVar<?>, Term<?>> result = substitutions;
		for (Tuple2<LVar<?>, Term<?>> binding : delta) {
			result = result.put(binding._1, binding._2);
		}
		return result;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Prefix && delta.equals(((Prefix) o).delta);
	}

	@Override
	public int hashCode() {
		return delta.hashCode();
	}

	@Override
	public String toString() {
		return "prefix" + delta;
	}
}
