package com.tgac.logic.lattice;

// ABOUTME: The ad-hoc propagator leaf for tests: a sync schema from its parts,
// ABOUTME: complete at construction. Production schemas are named classes.

import com.tgac.logic.constraints.store.Factor;
import com.tgac.logic.goals.Package;
import com.tgac.logic.unification.Term;
import io.vavr.collection.Array;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Test-side {@link Propagator} leaves: a schema assembled from its parts —
 * family empty, name, watched terms, body. The name contract is the
 * CALLER's here: the name must uniquely determine the body's semantics
 * within the family. Production propagators are named classes; no
 * production code builds ad-hoc leaves.
 */
public final class TestPropagators {

	private TestPropagators() {
	}

	public static <F extends Factor<F>> Propagator<F> of(
			F empty,
			String name,
			Iterable<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body) {
		return of(empty, name, watchedTerms, body, p -> false);
	}

	/** {@link #of} with the author's doom check. */
	public static <F extends Factor<F>> Propagator<F> of(
			F empty,
			String name,
			Iterable<? extends Term<?>> watchedTerms,
			BiFunction<Array<? extends Term<?>>, Package, Verdict> body,
			Predicate<Package> doom) {
		return new Leaf<>(Array.ofAll(watchedTerms), empty, name, body, doom);
	}

	private static final class Leaf<F extends Factor<F>> extends Propagator<F> {
		private final F empty;
		private final String name;
		private final BiFunction<Array<? extends Term<?>>, Package, Verdict> body;
		private final Predicate<Package> doom;

		private Leaf(Array<? extends Term<?>> watchedTerms, F empty, String name,
				BiFunction<Array<? extends Term<?>>, Package, Verdict> body,
				Predicate<Package> doom) {
			super(watchedTerms);
			this.empty = empty;
			this.name = name;
			this.body = body;
			this.doom = doom;
		}

		@Override
		public Verdict propagate(Package state) {
			return body.apply(watchedTerms(), state);
		}

		@Override
		public Propagator<F> watching(Array<? extends Term<?>> terms) {
			return new Leaf<>(terms, empty, name, body, doom);
		}

		@Override
		public F empty() {
			return empty;
		}

		@Override
		public boolean doomed(Package state) {
			return doom.test(state);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Class<? extends F> getFactorClass() {
			return (Class<? extends F>) empty.getClass();
		}
	}
}
