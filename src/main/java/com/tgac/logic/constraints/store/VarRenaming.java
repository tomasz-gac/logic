package com.tgac.logic.constraints.store;

// ABOUTME: Live vars to their targets — walkAll under a substitution fixed at
// ABOUTME: construction; a term mentioning none of them passes by identity.

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Substitutions;
import com.tgac.logic.unification.Term;
import io.vavr.collection.HashMap;
import java.util.Map;

/** Live vars to their targets: {@code walkAll} under a fixed substitution. */
final class VarRenaming implements Renaming {

	private final Substitutions substitutions;

	VarRenaming(Map<? extends Term<?>, Term<?>> seed) {
		this.substitutions = Substitutions.of(seed.entrySet().stream()
				// an identity entry means "keep" — walk's chain-follower
				// must never see a self-binding
				.filter(entry -> !entry.getKey().equals(entry.getValue()))
				.collect(HashMap.collector(entry -> varName(entry.getKey()), Map.Entry::getValue)));
	}

	private static LVar<?> varName(Term<?> name) {
		return name.asVar().<LVar<?>> map(var -> var)
				.getOrElseThrow(() -> new IllegalArgumentException(
						"a var renaming takes live var names — slot names cross by "
								+ "restating or minting: " + name));
	}

	@Override
	public Fiber<Term<?>> apply(Term<?> term) {
		return renamesAnyIn(term)
				? MiniKanren.walkAll(substitutions, term).map(t -> t)
				: Fiber.done(term);
	}

	/** walkAll rebuilds structure wholesale — an untouched term must pass by identity. */
	private boolean renamesAnyIn(Term<?> term) {
		return Renaming.namesIn(term)
				.anyMatch(name -> name.asVar().isDefined()
						&& substitutions.binding(name.asVar().get()) != null);
	}
}
