package com.tgac.logic.finitedomain;

// ABOUTME: A coupling carried by a residue: the LIVE propagator object plus its
// ABOUTME: watched-var slot map — replayed as a fresh instance over the given vars.

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.Array;
import java.util.List;
import lombok.Value;

/**
 * One live propagator, carried as ITSELF — the object plus a (watched var →
 * residue slot) map. The residue is a SCHEMA over its slots: replay
 * instantiates it by renaming, registering {@link Propagator#watching} the
 * given vars substituted at the mapped positions (grounds stay put) — each
 * consumption gets its own instance, so consumptions never couple to each
 * other. When the given vars ARE the watched vars (the master seeding its
 * body from the key), the renaming is the identity and the live object
 * re-activates as-is — which is what lets a recursive call project the same
 * object and land in the same table entry.
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

	Goal restate(List<Unifiable<?>> vars) {
		Propagator instance = varSlots.forAll(pair -> vars.get(pair._2) == pair._1)
				? propagator
				: propagator.watching(propagator.watchedTerms()
						.map(watched -> varSlots.find(pair -> pair._1 == watched)
								.<Term<?>> map(pair -> vars.get(pair._2))
								.getOrElse(watched)));
		return p -> Propagation.activate(instance)
				.apply(FiniteDomainConstraints.register(p));
	}
}
