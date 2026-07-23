package com.tgac.logic.finitedomain;

// ABOUTME: A coupling carried by a residue: the LIVE propagator object plus its
// ABOUTME: watched-var slot map — replayed by alias-unify and re-activation.

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Conjunction;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import java.util.List;
import lombok.Value;

/**
 * One live propagator, carried as ITSELF — no factory recipe, no shape token:
 * the object plus a (watched var → residue slot) map. Replay ALIAS-UNIFIES
 * each slot's live var with the propagator's original watched var and
 * re-activates the object: the body's closure reads its original vars, which
 * now walk to the live ones — custody and the Watches chain matcher walk
 * through aliases by design. On the call side the live vars ARE the original
 * vars and the unifications are no-ops.
 *
 * <p>Default field equality IS the intended identity semantics: the same
 * propagator object under the same slots (one store state, e.g. down a
 * recursion) compares equal; independent posts of a same-shaped coupling are
 * incomparable — a conservative false that costs reuse, never soundness.
 */
@Value(staticConstructor = "of")
public class CarriedConstraint {

	Propagator propagator;
	/** (original watched var, residue slot) — grounds need no entry. */
	Array<Tuple2<LVar<?>, Integer>> varSlots;

	@SuppressWarnings("unchecked")
	Goal restate(List<Unifiable<?>> vars) {
		Goal aliased = varSlots.foldLeft(Goal.success(), (goal, pair) ->
				Conjunction.of(goal, ((Unifiable<Object>) vars.get(pair._2))
						.unifies((Unifiable<Object>) pair._1)));
		return Conjunction.of(aliased,
				p -> Propagation.activate(propagator)
						.apply(FiniteDomainConstraints.register(p)));
	}
}
