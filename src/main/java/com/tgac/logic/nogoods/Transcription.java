package com.tgac.logic.nogoods;

// ABOUTME: Nogood literals under changed names: each posting row transcribes its
// ABOUTME: held content wrapped — never simplified into another representation.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.Posting;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.UnifyGoal;
import com.tgac.logic.constraints.store.Projectable;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Stored;
import com.tgac.logic.lattice.Imposition;
import com.tgac.logic.lattice.Propagator;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The hand-written transcription over the posting rows — the crossing keeps
 * every literal WRAPPED (nogood-store.md §7): terms rename through the
 * {@link Renaming}, ground data rides unchanged, items re-instantiate over
 * the renamed terms. Labels are presentation and drop; doom checks capture
 * lexical terms and reset to the safe default (never claim doom the renamed
 * lineage cannot lift); registrations are store-generic and carry. Content
 * this vocabulary cannot transcribe refuses loudly with its name — the
 * boundary never crosses knowledge silently.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Transcription {

	static Fiber<Nogood> nogood(Nogood nogood, Renaming renaming) {
		return nogood.getLiterals().foldLeft(
						Fiber.<List<Posting>> done(List.empty()),
						(acc, literal) -> acc.flatMap(literals ->
								literal(literal, renaming).map(literals::append)))
				.map(Nogood::of);
	}

	@SuppressWarnings("unchecked")
	static Fiber<Posting> literal(Posting literal, Renaming renaming) {
		if (literal instanceof Posting.Named) {
			return literal(((Posting.Named) literal).getInner(), renaming);
		}
		if (literal instanceof UnifyGoal) {
			UnifyGoal<?> bind = (UnifyGoal<?>) literal;
			return renaming.apply(bind.getU())
					.flatMap(u -> renaming.apply(bind.getV())
							.map(v -> UnifyGoal.of(
									(Term<Object>) u, (Term<Object>) v, bind.isNoCheck())));
		}
		if (literal instanceof Posting.Activation) {
			Posting.Activation activation = (Posting.Activation) literal;
			return item(activation.getItem(), renaming)
					.map(renamed -> Propagation.activate(
							renamed, activation.getRegistration(), p -> false));
		}
		if (literal instanceof Posting.Absorption) {
			Posting.Absorption absorption = (Posting.Absorption) literal;
			if (!(absorption.getFactor() instanceof Projectable)) {
				throw new IllegalStateException(
						"nogood literal absorbs a factor that cannot cross: " + absorption);
			}
			return ((Projectable<?>) absorption.getFactor()).rename(renaming)
					.flatMap(factor -> terms(absorption.getDeclared(), renaming)
							.map(declared -> Propagation.absorb(factor, declared)));
		}
		if (literal instanceof Posting.AllOf) {
			return ((Posting.AllOf) literal).getParts().foldLeft(
							Fiber.<List<Posting>> done(List.empty()),
							(acc, part) -> acc.flatMap(parts ->
									literal(part, renaming).map(parts::append)))
					.map(parts -> Posting.all(parts.toJavaArray(Posting[]::new)));
		}
		throw new IllegalStateException(
				"nogood literal cannot cross the boundary: " + literal);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Fiber<Stored> item(Stored item, Renaming renaming) {
		if (item instanceof Imposition) {
			Imposition<?> imposition = (Imposition<?>) item;
			return renaming.apply(imposition.getTarget())
					.map(target -> new Imposition(
							imposition.getStoreClass(), target, imposition.getValue()));
		}
		if (item instanceof Propagator) {
			Propagator propagator = (Propagator) item;
			return propagator.watchedTerms().foldLeft(
							Fiber.<List<Term<?>>> done(List.empty()),
							(acc, term) -> acc.flatMap(terms ->
									renaming.apply(term).map(terms::append)))
					.map(terms -> propagator.watching(Array.ofAll(terms)));
		}
		if (item instanceof Nogood) {
			return nogood((Nogood) item, renaming).map(n -> n);
		}
		throw new IllegalStateException(
				"stated item cannot cross the boundary: " + item);
	}

	private static Fiber<List<Term<?>>> terms(List<Term<?>> declared, Renaming renaming) {
		return declared.foldLeft(
				Fiber.<List<Term<?>>> done(List.empty()),
				(acc, term) -> acc.flatMap(terms ->
						renaming.apply(term).map(terms::append)));
	}
}
