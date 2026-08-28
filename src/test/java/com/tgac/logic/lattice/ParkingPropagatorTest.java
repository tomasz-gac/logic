package com.tgac.logic.lattice;

// ABOUTME: The parking propagator's schema contract: identity by (family, name,
// ABOUTME: watched terms), kind-distinct from the sync Propagator, watch matching.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import java.util.Collections;
import org.junit.Test;

public class ParkingPropagatorTest {

	/** A minimal parking schema over one watched term, always keeping. */
	private static final class Keeping extends ParkingPropagator<FlatConstraints> {
		private Keeping(Array<? extends Term<?>> watchedTerms) {
			super(watchedTerms);
		}

		static Keeping on(Term<?> term) {
			return new Keeping(Array.of(term));
		}

		@Override
		public Fiber<Verdict> propagate(Package state) {
			return Fiber.done(Verdict.keep());
		}

		@Override
		public ParkingPropagator<FlatConstraints> watching(Array<? extends Term<?>> terms) {
			return new Keeping(terms);
		}

		@Override
		public FlatConstraints empty() {
			return FlatConstraints.empty();
		}

		@Override
		public String name() {
			return "keeping";
		}

		@Override
		public Class<? extends FlatConstraints> getFactorClass() {
			return FlatConstraints.class;
		}
	}

	@Test
	public void identityIsFamilyNameAndWatchedTerms() {
		Unifiable<Integer> x = lvar();
		assertThat(Keeping.on(x)).isEqualTo(Keeping.on(x));
		assertThat(Keeping.on(x)).isNotEqualTo(Keeping.on(lvar()));
	}

	@Test
	public void neverEqualToASyncPropagatorOfTheSameStatement() {
		// the examination lane is part of the kind: a parking schema and a
		// sync schema are different knowledge even over one name and term
		Unifiable<Integer> x = lvar();
		Propagator<FlatConstraints> sync = TestPropagators.of(FlatConstraints.empty(), "keeping",
				Collections.<Term<?>> singletonList(x),
				(watched, pkg) -> Verdict.keep());
		assertThat(Keeping.on(x)).isNotEqualTo(sync);
		assertThat(sync).isNotEqualTo(Keeping.on(x));
	}

	@Test
	public void watchesItsTermsThroughTheChain() {
		Unifiable<Integer> x = lvar();
		Keeping parked = Keeping.on(x);
		assertThat(parked.watches(Package.empty(), x.getObjectTerm())).isTrue();
		assertThat(parked.watches(Package.empty(), lvar().getObjectTerm())).isFalse();
	}
}
