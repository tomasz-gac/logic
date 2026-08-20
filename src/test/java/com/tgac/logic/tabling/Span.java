package com.tgac.logic.tabling;

// ABOUTME: A minimal atom for carrier tests: one closed interval of longs,
// ABOUTME: meet = intersection - the value algebra without a live solver store.

import com.tgac.functional.algebra.Semilattice;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Traversable;
import java.util.Collections;
import lombok.Value;

/**
 * The smallest atom that can sit in a residue conjunct: an interval whose
 * meet is intersection (empty canonicalized, so equal regions compare
 * equal). Only the VALUE face is real — a conjunct inside a
 * {@link Condition} is data, never driven, so the family token is an
 * abstract class and the seeding face refuses.
 */
@Value
class Span implements Atom<Span.SpanConstraints>, Semilattice<Span> {

	/** The family token: never instantiated — spans are data, never driven. */
	abstract static class SpanConstraints implements Factor<SpanConstraints> {
	}

	long lo;
	long hi;

	static Span of(long lo, long hi) {
		return lo <= hi ? new Span(lo, hi) : new Span(1L, 0L);
	}

	/** A one-theory conjunct holding this interval. */
	static Residues factor(long lo, long hi) {
		return Residues.of(HashMap.of(SpanConstraints.class,
				Theory.of(Collections.singletonList(of(lo, hi)))));
	}

	@Override
	public Span combine(Span other) {
		return of(Math.max(lo, other.lo), Math.min(hi, other.hi));
	}

	/** Sharp over spans: narrower entails wider — the meet-absorption order. */
	@Override
	public boolean leq(Atom<SpanConstraints> other) {
		if (other instanceof Span) {
			return combine((Span) other).equals(this);
		}
		return equals(other);
	}

	@Override
	public Class<? extends SpanConstraints> getFactorClass() {
		return SpanConstraints.class;
	}

	@Override
	public String name() {
		return "span";
	}

	@Override
	public Traversable<Term<?>> watched() {
		return HashSet.empty();
	}

	/** Ground data: nothing to rename. */
	@Override
	public Fiber<Atom<SpanConstraints>> rename(Renaming renaming) {
		return Fiber.done(this);
	}

	@Override
	public SpanConstraints empty() {
		throw new UnsupportedOperationException("value-only test atom");
	}
}
