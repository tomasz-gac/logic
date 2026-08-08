package com.tgac.logic.unification;

// ABOUTME: Capability of terms that a solver emits — answers are data, not goals.
// ABOUTME: Ground values carry it alongside Unifiable; reified variables carry only this.

/**
 * A term that comes out of a solver. Reified terms carry no goal-building
 * capability: they cannot re-enter unification.
 *
 * @author TGa
 */
public interface Reified<T> extends Term<T> {

	/**
	 * A ground answer denotes exactly one value: no free name anywhere in the
	 * term and no residual constraints riding it. Carriers that express
	 * infinities override this to say so.
	 */
	default boolean isGround() {
		return !asReified().isDefined()
				&& !MiniKanren.namesIn(this).findFirst().isPresent();
	}
}
