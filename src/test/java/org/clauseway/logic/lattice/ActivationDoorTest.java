package org.clauseway.logic.lattice;

// ABOUTME: The one activation door: registration seeds from the atom's own
// ABOUTME: empty; doom is read as a declared capability, absent means price 1.

import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.functional.fibers.Fiber;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.constraints.Propagation;
import org.clauseway.logic.constraints.Trial;
import org.clauseway.logic.constraints.store.Constraint;
import org.clauseway.logic.constraints.store.Atom;
import org.clauseway.logic.constraints.store.Renaming;
import org.clauseway.logic.constraints.store.Theory;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.lattice.LatticeFactorTest.FlatConstraints;
import org.clauseway.logic.lattice.LatticeFactorTest.FlatSet;
import org.clauseway.logic.unification.Term;
import org.clauseway.logic.unification.Unifiable;
import io.vavr.collection.HashSet;
import io.vavr.collection.Traversable;
import org.junit.Test;

public class ActivationDoorTest {

	private static final Unifiable<Integer> X = lvar();

	private static Imposition<FlatSet, FlatConstraints> on(Term<?> target, Object... values) {
		return new Imposition<>(FlatConstraints.class, target, FlatSet.of(values), FlatConstraints.empty());
	}

	@Test
	public void activationSeedsTheAbsentFamilyFromTheAtomsEmpty() {
		Package state = new BreadthFirstScheduler<>(Trial.imposed(
				Propagation.activate(on(X, 1, 2)), Package.empty())).get().head();

		Theory<FlatConstraints> theory = Constraint.in(state, FlatConstraints.class).get().getTheory();
		assertThat(FlatConstraints.empty().getValue(theory, (Term<?>) X).get()).isEqualTo(FlatSet.of(1, 2));
	}

	@Test
	public void aCoveredStatementRidesThroughByIdentity() {
		// the statement covering guard: the resident already carries the
		// atom's knowledge — the door's meet returns the receiver itself,
		// nothing enqueues, the package is the SAME object
		Package seeded = new BreadthFirstScheduler<>(Trial.imposed(
				Propagation.activate(on(X, 1, 2)), Package.empty())).get().head();

		Package again = new BreadthFirstScheduler<>(Trial.imposed(
				Propagation.activate(on(X, 1, 2, 3)), seeded)).get().head();

		assertThat(again).isSameAs(seeded);
	}

	@Test
	public void doomIsReadThroughTheDeclaredCapability() {
		// Imposition declares Doomed: the door wires the atom's own check
		// into the statement — a ground target the value refuses is doomed
		assertThat(Propagation.activate(on(lval(5), 1, 2)).doomed(Package.empty()))
				.isTrue();
		assertThat(Propagation.activate(on(lval(1), 1, 2)).doomed(Package.empty()))
				.isFalse();
	}

	@Test
	public void anAtomWithoutTheCapabilityClaimsNothing() {
		Atom<FlatConstraints> plain = new Atom<FlatConstraints>() {
			@Override
			public FlatConstraints empty() {
				return FlatConstraints.empty();
			}

			@Override
			public Class<? extends FlatConstraints> getFactorClass() {
				return FlatConstraints.class;
			}

			@Override
			public String name() {
				return "plain";
			}

			@Override
			public Traversable<Term<?>> watched() {
				return HashSet.of((Term<?>) X);
			}

			@Override
			public Fiber<Atom<FlatConstraints>> rename(Renaming renaming) {
				return Fiber.done(this);
			}
		};

		assertThat(Propagation.activate(plain).doomed(Package.empty())).isFalse();
	}
}
