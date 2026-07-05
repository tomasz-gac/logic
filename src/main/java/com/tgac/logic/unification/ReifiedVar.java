package com.tgac.logic.unification;

// ABOUTME: A canonical hole in a reified answer: the output counterpart of LVar.
// ABOUTME: Equal by name — reification numbers holes canonically, so name equality is alpha-equivalence.

import io.vavr.control.Option;
import lombok.Value;

/**
 * A variable position in a reified answer. Where an {@link LVar} is a
 * binding site identified by object identity, a reified var is a canonical
 * token identified by its name: two answers sharing the shape share the
 * names, which is what makes plain equality on reified terms mean
 * alpha-equivalence.
 *
 * @author TGa
 */
@Value(staticConstructor = "of")
public class ReifiedVar<T> implements Reified<T> {
	String name;

	@Override
	public Option<ReifiedVar<T>> asReified() {
		return Option.of(this);
	}

	@Override
	public String toString() {
		return "<" + name + ">";
	}
}
