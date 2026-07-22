package com.tgac.logic.finitedomain;

// ABOUTME: A coupling carried by a residue: the factory recipe plus its args as
// ABOUTME: residue slots or ground terms — replayable, alpha-stable, comparable.

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.Value;

/**
 * One live propagator, made expressible: the RECIPE is the factory's static
 * rebuild constant (identity equality — one instance per factory — is what
 * makes equal coupling shapes yield EQUAL residues, hence alpha-stable keys),
 * and {@code args} are the factory's arguments in order, each either an
 * {@code Integer} residue slot or a ground {@link Unifiable}. Replay maps
 * slots to live vars and re-invokes the factory — a public post, propagated
 * like freshly stated knowledge.
 */
@Value(staticConstructor = "of")
public class CarriedConstraint {

	Function<List<Unifiable<?>>, Goal> recipe;
	Array<Object> args;

	Goal restate(List<Unifiable<?>> vars) {
		List<Unifiable<?>> actual = new ArrayList<>();
		for (Object arg : args) {
			actual.add(arg instanceof Integer ? vars.get((Integer) arg) : (Unifiable<?>) arg);
		}
		return recipe.apply(actual);
	}
}
