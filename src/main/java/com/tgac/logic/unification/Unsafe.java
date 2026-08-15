package com.tgac.logic.unification;

// ABOUTME: The unifier's operations grounded on a deliberately built private
// ABOUTME: engine — for synchronous seams over pure, single-completion fibers.

import com.tgac.functional.fibers.MFiber;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import io.vavr.control.Option;

/**
 * Deliberate private-engine grounding of the unifier's fibers, for call
 * sites behind a synchronous seam (pricing, the trial's fast check, joins,
 * display renders). SAFE only because these fibers are pure plumbing —
 * walks, renames, unification folds: no parks (nothing to await), no forks
 * (single completion), no channels — so the private engine cannot strand,
 * drop answers, or couple to any outer run. Code that can carry the fiber
 * composes {@link MiniKanren} directly; grounding a fiber that parks or
 * forks here is the misuse the name warns about.
 */
public final class Unsafe {

	private Unsafe() {
	}

	/** {@link MiniKanren#walkAll}, grounded. */
	public static <T> Term<T> walkAll(Substitutions s, Term<T> u) {
		return new BreadthFirstScheduler<>(MiniKanren.walkAll(s, u)).get();
	}

	/** {@link MiniKanren#unify}, grounded. */
	public static <T> Option<Substitutions> unify(Substitutions s, Term<T> lhs, Term<T> rhs) {
		return ground(MiniKanren.unify(s, lhs, rhs));
	}

	/** {@link MiniKanren#unifyPrefix}, grounded. */
	public static <T> Option<Prefix> unifyPrefix(Substitutions s, Term<T> lhs, Term<T> rhs) {
		return ground(MiniKanren.unifyPrefix(s, lhs, rhs));
	}

	/** {@link MiniKanren#unifyPrefixUnsafe} (no occurs check), grounded. */
	public static <T> Option<Prefix> unifyPrefixUnsafe(Substitutions s, Term<T> lhs, Term<T> rhs) {
		return ground(MiniKanren.unifyPrefixUnsafe(s, lhs, rhs));
	}

	/** {@link MiniKanren#reify}, grounded. */
	public static <T> Reified<T> reify(Substitutions s, Term<T> item) {
		return new BreadthFirstScheduler<>(MiniKanren.reify(s, item)).get();
	}

	private static <A> Option<A> ground(MFiber<A> m) {
		return new BreadthFirstScheduler<>(m.getFiber()).get();
	}
}
