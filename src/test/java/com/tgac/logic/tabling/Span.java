package com.tgac.logic.tabling;

// ABOUTME: A minimal Projectable for carrier tests: one closed interval of longs,
// ABOUTME: meet = intersection - the value algebra without a live solver store.

import com.tgac.logic.constraints.store.Atom;
import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Constraint;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Packaged;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import java.util.List;
import lombok.Value;

/**
 * The smallest store that can sit in a residue conjunct: an interval whose
 * meet is intersection (empty canonicalized, so equal regions compare
 * equal). Only the VALUE face is real — the solver-side triggers throw,
 * because a conjunct inside a {@link Condition} is data, never driven.
 */
@Value
class Span implements Constraint<Span> {
	long lo;
	long hi;

	static Span of(long lo, long hi) {
		return lo <= hi ? new Span(lo, hi) : new Span(1L, 0L);
	}

	/** A one-factor conjunct holding this interval. */
	static Residues factor(long lo, long hi) {
		return Residues.of(HashMap.of(Span.class, of(lo, hi)));
	}

	@Override
	public Span meet(Span other) {
		return of(Math.max(lo, other.lo), Math.min(hi, other.hi));
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public Tuple2<Span, Span> split(List<LVar<?>> vars) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public Fiber<Span> rename(Renaming renaming) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public Fiber<Revision> normalize(Package state) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public Fiber<Revision> normalize(Prefix prefix, Package state) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public <A> Term<A> reify(Term<A> unifiable, Substitutions renameSubstitutions, Package p) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public Span meet(Atom c) {
		throw new UnsupportedOperationException("value-only test store");
	}

	@Override
	public boolean contains(Atom c) {
		throw new UnsupportedOperationException("value-only test store");
	}
}
