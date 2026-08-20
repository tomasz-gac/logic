package com.tgac.logic.lattice;

// ABOUTME: The one activation door: registration seeds from the atom's own
// ABOUTME: empty; doom is read as a declared capability, absent means price 1.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.Trial;
import com.tgac.logic.constraints.store.Atom;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
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

		FlatConstraints store = (FlatConstraints) state.getStores()
				.get(FlatConstraints.class).get();
		assertThat(store.getValue((Term<?>) X).get()).isEqualTo(FlatSet.of(1, 2));
	}

	@Test
	public void doomIsReadThroughTheDeclaredCapability() {
		// Imposition declares Doomed: a ground target the value refuses prices 0
		assertThat(Propagation.activate(on(lval(5), 1, 2)).answers(Package.empty()))
				.isEqualTo(0);
		assertThat(Propagation.activate(on(lval(1), 1, 2)).answers(Package.empty()))
				.isEqualTo(1);
	}

	@Test
	public void anAtomWithoutTheCapabilityPricesAsUnknown() {
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

		assertThat(Propagation.activate(plain).answers(Package.empty())).isEqualTo(1);
	}
}
