package com.tgac.logic.lattice;

// ABOUTME: The parking propagator's schema contract: identity by (family, name,
// ABOUTME: watched terms), kind-distinct from the sync Propagator, watch matching.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.Fiber;
import com.tgac.logic.TestSchedulers;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.lattice.LatticeFactorTest.FlatConstraints;
import com.tgac.logic.lattice.LatticeFactorTest.FlatSet;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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

	// ---- the wiring: the store administers the parking kind ----

	private static Goal flat(Unifiable<?> x, FlatSet values) {
		return FlatConstraints.empty().impose(x, values);
	}

	/** The flat family's even check, answered through the fiber lane. */
	private static Goal parkingEven(Unifiable<Integer> x) {
		return Propagation.activate(TestPropagators.parking(FlatConstraints.empty(), "parkingEven",
				Collections.<Term<?>> singletonList(x),
				(watched, pkg) -> Fiber.defer(() -> {
					Term<?> w = pkg.walk(watched.get(0));
					if (!w.isVal()) {
						return Fiber.done(Verdict.keep());
					}
					return Fiber.done(((Integer) w.get()) % 2 == 0 ?
							Verdict.subsumed() :
							Verdict.fail());
				})));
	}

	@Test
	public void examinesThroughTheFiberLane() {
		// first examination on arrival (keep), wake on the binding, verdict
		// consumed — the same lifecycle as a sync propagator, fiber-shaped
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2, 3, 4))
				.and(parkingEven(x))
				.and(x.unifies(4))
				.solve(x, TestSchedulers.factory()).count())
				.isEqualTo(1);

		Unifiable<Integer> y = lvar();
		assertThat(flat(y, FlatSet.of(1, 2, 3, 4))
				.and(parkingEven(y))
				.and(y.unifies(3))
				.solve(y, TestSchedulers.factory()).count())
				.isZero();
	}

	/**
	 * A parking schema over [trigger, target]: once the trigger grounds, its
	 * verdict narrows the target to a point — read positionally through the
	 * watched terms, per the schema contract.
	 */
	private static Goal narrowsOnGround(Unifiable<Integer> trigger, Unifiable<Integer> target,
			int point) {
		return Propagation.activate(TestPropagators.parking(FlatConstraints.empty(),
				"narrowsOnGround" + point,
				Arrays.<Term<?>> asList(trigger, target),
				(watched, pkg) -> Fiber.defer(() -> {
					Term<?> t = pkg.walk(watched.get(0));
					if (!t.isVal()) {
						return Fiber.done(Verdict.keep());
					}
					return Fiber.done(Verdict.update((live, theory) ->
							FlatConstraints.empty().update(
									(Theory<FlatConstraints>) theory, live,
									live.walk(watched.get(1)), FlatSet.of(point))));
				})));
	}

	@Test
	public void aParkingVerdictCascadesIntoTheStore() {
		// the parking verdict's update collapses the target to a point — the
		// inferred binding lands through the chokepoint like any other
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		List<Integer> answers = flat(x, FlatSet.of(1, 2, 3, 4))
				.and(narrowsOnGround(y, x, 4))
				.and(y.unifies(0))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(4);
	}

	@Test
	public void mixedLanesCascadeTogether() {
		// the parking verdict narrows x to an odd point; the SYNC even check
		// wakes on the collapse and vetoes — one cascade, both lanes
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		assertThat(flat(x, FlatSet.of(1, 2, 3, 4))
				.and(syncEven(x))
				.and(narrowsOnGround(y, x, 3))
				.and(y.unifies(0))
				.solve(x, TestSchedulers.factory()).count())
				.isZero();
	}

	/** The sync twin: even passes, odd fails — wrapped in done at the store's call site. */
	private static Goal syncEven(Unifiable<Integer> x) {
		return Propagation.activate(
				TestPropagators.of(FlatConstraints.empty(), "even",
						Collections.<Term<?>> singletonList(x),
						(watched, pkg) -> {
							Term<?> w = pkg.walk(watched.get(0));
							if (!w.isVal()) {
								return Verdict.keep();
							}
							return ((Integer) w.get()) % 2 == 0 ?
									Verdict.subsumed() :
									Verdict.fail();
						}));
	}
}
