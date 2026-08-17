package com.tgac.logic.constraints;

// ABOUTME: The rename machinery as the vocabulary's first visitor: every row
// ABOUTME: transcribes wrapped; content that cannot cross refuses with its name.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.unification.Term;
import io.vavr.collection.List;
import lombok.RequiredArgsConstructor;

/**
 * Terms rename through the {@link Renaming}, ground data rides unchanged,
 * items re-instantiate over the renamed terms ;
 * labels drop (the {@link Posting.Visitor} default), doom checks reset to
 * the safe default, registrations carry.
 */
@RequiredArgsConstructor
final class Renamer implements Posting.Visitor<Fiber<Posting>> {

	private final Renaming renaming;

	@Override
	@SuppressWarnings("unchecked")
	public Fiber<Posting> visit(UnifyGoal<?> unification) {
		UnifyGoal<Object> bind = (UnifyGoal<Object>) unification;
		return renaming.apply(bind.getU())
				.flatMap(u -> renaming.apply(bind.getV())
						.map(v -> UnifyGoal.of((Term<Object>) u, (Term<Object>) v, bind.isNoCheck())));
	}

	/**
	 * A prefix is bindings, so it crosses as the conjunction of its binds —
	 * each pair re-keyed; the checked mint is lineage-local and unification
	 * is its portable spelling, re-imposed through the unifier on arrival.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Fiber<Posting> visit(Posting.Resolution resolution) {
		return List.ofAll(resolution.getPrefix().bindings()).foldLeft(
						Fiber.<List<Posting>> done(List.empty()),
						(acc, binding) -> acc.flatMap(binds ->
								renaming.apply((Term<?>) binding._1)
										.flatMap(lhs -> renaming.apply(binding._2)
												.map(rhs -> binds.append(UnifyGoal.of(
														(Term<Object>) lhs, (Term<Object>) rhs, false))))))
				.map(binds -> binds.size() == 1
						? binds.head()
						: Posting.all(binds.toJavaArray(Posting[]::new)));
	}

	@Override
	public Fiber<Posting> visit(Posting.Activation activation) {
		return activation.getItem().rename(renaming)
				.map(renamed -> Propagation.activate(
						renamed, activation.getRegistration(), p -> false));
	}

	@Override
	public Fiber<Posting> visit(Posting.Absorption absorption) {
		return absorption.getFactor().rename(renaming)
				.flatMap(renamed -> absorption.getDeclared().foldLeft(
								Fiber.<List<Term<?>>> done(List.empty()),
								(acc, term) -> acc.flatMap(terms ->
										renaming.apply(term).map(terms::append)))
						.map(terms -> Propagation.absorb(renamed, terms)));
	}

	/** Parts transcribe wrapped; the joint doom resets to the safe default. */
	@Override
	public Fiber<Posting> visit(Posting.AllOf all) {
		return all.getParts().foldLeft(
						Fiber.<List<Posting>> done(List.empty()),
						(acc, part) -> acc.flatMap(renamed ->
								part.accept(this).map(renamed::append)))
				.map(renamed -> Posting.all(renamed.toJavaArray(Posting[]::new)));
	}
}
