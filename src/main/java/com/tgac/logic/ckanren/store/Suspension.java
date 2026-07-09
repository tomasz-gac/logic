package com.tgac.logic.ckanren.store;

// ABOUTME: A parked search effect: run the body once the condition over the shared
// ABOUTME: substitution holds. The condition must be monotone: once true, stays true.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import java.util.function.Predicate;

/**
 * {@code (watched, ripe, body)}: the driver re-examines the suspension when a
 * watched chain binds; when {@code ripe} holds, the body joins the run lane and
 * the suspension is gone — fired once, forever. A store may emit one via
 * {@code Revision.withSuspend}; the degenerate form (no watched terms, always
 * ripe) is an immediate run.
 *
 * <p><b>The ripeness contract.</b> {@code ripe} receives the {@link
 * Substitutions} view, so it is structurally scoped to shared knowledge: it
 * cannot see domains, records or any other store factor — factor-conditioned
 * reactions belong to the store that owns the factor. What the type cannot
 * enforce is MONOTONICITY, so here it is, literally: once {@code ripe} is true
 * in some state, it must remain true in every state derived from it —
 * equivalently, ADDING BINDINGS MUST NEVER FALSIFY THE CONDITION (the predicate
 * must be upward-closed in the substitution order). The driver only samples the
 * condition at statement time and when a watched chain binds; monotonicity is
 * exactly what makes that lazy sampling as good as watching continuously, and
 * what keeps firing independent of scheduler and agenda order. Rule of thumb:
 * conditions about the PRESENCE of knowledge qualify ("x is ground", "x and y
 * are both bound", "x == y is decided"); conditions about its ABSENCE do not
 * ("x is still unbound", "fewer than two are bound") — those are
 * negation-as-failure, whose home is committed choice, not the suspension lane.
 */
public final class Suspension {

	private final Iterable<? extends Term<?>> watched;
	private final Predicate<Substitutions> ripe;
	private final Goal body;

	private Suspension(Iterable<? extends Term<?>> watched, Predicate<Substitutions> ripe, Goal body) {
		this.watched = watched;
		this.ripe = ripe;
		this.body = body;
	}

	public static Suspension of(Iterable<? extends Term<?>> watched, Predicate<Substitutions> ripe, Goal body) {
		return new Suspension(watched, ripe, body);
	}

	public boolean isRipe(Package state) {
		return ripe.test(state.substitution());
	}

	public boolean watchesAny(Package state, Term<?> changed) {
		for (Term<?> w : watched) {
			if (Watches.matches(state, w, changed)) {
				return true;
			}
		}
		return false;
	}

	public Goal body() {
		return body;
	}

	@Override
	public String toString() {
		return "suspend" + watched;
	}
}
