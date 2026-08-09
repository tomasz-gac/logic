package com.tgac.logic.notes;

// ABOUTME: The note chassis: a set of records living by the four moves — cross off,
// ABOUTME: enforce the last, fail on empty, discard when satisfied. Cargo owns verify.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.ConstraintStore;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Revision;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Prefix;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashSet;
import io.vavr.control.Option;
import java.util.HashSet;
import java.util.Set;

/**
 * A bag of records, each a note: "at least one escape must hold." The store's
 * lattice is record union (more notes = more known; leq = containment), its
 * normal form is wholesale re-verification, and every record lives by the
 * four moves. What a record's escapes ARE — unification pairs, lattice boxes,
 * whole packs — is the cargo's business: {@link #verify} answers per record
 * with the three-way verdict, and the chassis routes the moves.
 */
public abstract class Notes<R extends Stored, S extends Notes<R, S>> implements Projectable<S> {

	protected abstract LinkedHashSet<R> records();

	protected abstract S make(LinkedHashSet<R> records);

	protected abstract Class<R> recordClass();

	/** none = the branch fails; some(none) = satisfied, discard; some(record) = keep, simplified. */
	protected abstract Option<Option<R>> verify(R record, Package state);

	/** Does the record's whole surface fall within {@code covered}? Decides {@link #split}. */
	protected abstract boolean fits(R record, Set<Term<?>> covered);

	/** The record with its names translated through the renaming. */
	protected abstract Fiber<R> renamed(R record, Renaming renaming);

	@Override
	public S meet(S other) {
		return make(records().addAll(other.records()));
	}

	/** Record containment directly — the order the union-meet derives. */
	@Override
	public boolean leq(S other) {
		return records().containsAll(other.records());
	}

	@Override
	public boolean isEmpty() {
		return records().isEmpty();
	}

	@Override
	public ConstraintStore remove(Stored c) {
		return make(records().remove(recordClass().cast(c)));
	}

	@Override
	public ConstraintStore prepend(Stored c) {
		return make(records().add(recordClass().cast(c)));
	}

	@Override
	public boolean contains(Stored c) {
		return recordClass().isInstance(c) && records().contains(recordClass().cast(c));
	}

	@Override
	public <T> Goal enforce(Term<T> x) {
		return Goal.success();
	}

	/** Wholesale re-verification IS the normal form: every record re-examined, the four moves routed. */
	@Override
	public Fiber<Revision> normalize(Package state) {
		return Fiber.done(verifyAll(state)
				.map(kept -> (Revision) Revision.updated(make(kept)))
				.getOrElse(Revision::fail));
	}

	@Override
	public Fiber<Revision> revise(Prefix prefix, Package state) {
		// the reaction was always wholesale — revise is normalize by another trigger
		return normalize(state);
	}

	private Option<LinkedHashSet<R>> verifyAll(Package state) {
		LinkedHashSet<R> kept = LinkedHashSet.empty();
		for (R record : records()) {
			Option<Option<R>> verdict = verify(record, state);
			if (!verdict.isDefined()) {
				return Option.none();
			}
			for (R survivor : verdict.get()) {
				kept = kept.add(survivor);
			}
		}
		return Option.of(kept);
	}

	/** A record goes to the covered half iff its whole surface is supplied; {@code _1 ∧ _2 = this}. */
	@Override
	public Tuple2<S, S> split(java.util.List<LVar<?>> vars) {
		Set<Term<?>> covered = new HashSet<>(vars);
		LinkedHashSet<R> in = LinkedHashSet.empty();
		LinkedHashSet<R> out = LinkedHashSet.empty();
		for (R record : records()) {
			if (fits(record, covered)) {
				in = in.add(record);
			} else {
				out = out.add(record);
			}
		}
		return Tuple.of(make(in), make(out));
	}

	@Override
	public Fiber<S> rename(Renaming renaming) {
		return records().foldLeft(
						Fiber.<LinkedHashSet<R>> done(LinkedHashSet.empty()),
						(acc, record) -> acc.flatMap(records ->
								renamed(record, renaming).map(records::add)))
				.map(this::make);
	}
}
