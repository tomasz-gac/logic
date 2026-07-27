package com.tgac.logic.lattice;

// ABOUTME: Proves the lattice store is generic: a flat set-of-values instance gets
// ABOUTME: verification, narrowing, collapse, propagators, split and rename for free.

import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.control.Option;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.Test;

/**
 * The second store instance (docs/design/lattice-store.md §2): a flat lattice of
 * arbitrary values — meet is intersection, a point is a size-one set. Everything
 * asserted here (ground verification, narrowing, collapse to an inferred
 * binding, named propagators waking on their terms, split and canonical rename)
 * is inherited machinery; the component lattice carries its capability record
 * and the store supplies only its construction seams.
 */
public class LatticeStoreTest {

	/** The component lattice: a finite set of admissible values. */
	@Value
	static class FlatSet implements Domain<FlatSet> {
		HashSet<Object> values;

		static FlatSet of(Object... vs) {
			return new FlatSet(HashSet.of(vs));
		}

		@Override
		public FlatSet meet(FlatSet other) {
			return new FlatSet(values.intersect(other.values));
		}

		@Override
		public boolean leq(FlatSet other) {
			return other.values.containsAll(values);
		}

		@Override
		public boolean isAbsorbing() {
			return values.isEmpty();
		}

		@Override
		public boolean admits(Object ground) {
			return values.contains(ground);
		}

		@Override
		public Option<Object> asPoint() {
			return values.size() == 1 ? Option.of(values.head()) : Option.none();
		}

		@Override
		public String toString() {
			return values.mkString("{", ",", "}");
		}
	}

	/** The store: nothing but its construction seams. */
	static final class FlatConstraints extends LatticeStore<FlatSet, FlatConstraints> {
		private static final FlatConstraints EMPTY =
				new FlatConstraints(LinkedHashMap.empty(), HashSet.empty());
		private static final FlatConstraints BOTTOM =
				new FlatConstraints(LinkedHashMap.empty(), HashSet.empty());

		private FlatConstraints(LinkedHashMap<Term<?>, FlatSet> values, HashSet<Propagator> propagators) {
			super(values, propagators);
		}

		static FlatConstraints empty() {
			return EMPTY;
		}

		@Override
		protected FlatConstraints create(LinkedHashMap<Term<?>, FlatSet> values, HashSet<Propagator> propagators) {
			return new FlatConstraints(values, propagators);
		}

		@Override
		protected FlatConstraints bottomStore() {
			return BOTTOM;
		}

		@Override
		public <T> Goal enforce(Term<T> x) {
			return Goal.success();
		}
	}

	private static Goal flat(Unifiable<?> x, FlatSet values) {
		return FlatConstraints.empty().impose(x, values);
	}

	@Test
	public void aGroundBindingIsVerifiedAgainstTheFlatValue() {
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2)).and(x.unifies(1)).solve(x).count())
				.isEqualTo(1);

		Unifiable<Integer> y = lvar();
		assertThat(flat(y, FlatSet.of(1, 2)).and(y.unifies(3)).solve(y).count())
				.isZero();
	}

	@Test
	public void meetNarrowsAndACollapseInfersTheBinding() {
		Unifiable<Integer> x = lvar();
		List<Integer> answers = flat(x, FlatSet.of(1, 2))
				.and(flat(x, FlatSet.of(2, 3)))
				.solve(x)
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(2);
	}

	@Test
	public void disjointImpositionsFail() {
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2)).and(flat(x, FlatSet.of(3, 4))).solve(x).count())
				.isZero();
	}

	@Test
	public void aNamedPropagatorWakesOnItsWatchedTerm() {
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2, 3, 4)).and(evenO(x)).and(x.unifies(4)).solve(x).count())
				.isEqualTo(1);

		Unifiable<Integer> y = lvar();
		assertThat(flat(y, FlatSet.of(1, 2, 3, 4)).and(evenO(y)).and(y.unifies(3)).solve(y).count())
				.isZero();
	}

	/** A parked constraint: once its variable grounds, even passes, odd fails. */
	private static Goal evenO(Unifiable<Integer> x) {
		return s -> Propagation.activate(
						Propagator.of(FlatConstraints.class, "even",
								Collections.<Term<?>> singletonList(x),
								(watched, pkg) -> {
									Term<?> w = pkg.walk(watched.get(0));
									if (!w.isVal()) {
										return Verdict.keep();
									}
									return ((Integer) w.get()) % 2 == 0 ? Verdict.subsumed() : Verdict.fail();
								}))
				.apply(s.getStores().containsKey(FlatConstraints.class) ? s : s.withStore(FlatConstraints.empty()));
	}

	@Test
	public void splitPartitionsByNameAndRenameReKeys() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		FlatConstraints store = FlatConstraints.empty()
				.withValue(x, FlatSet.of(1, 2))
				.withValue(y, FlatSet.of(3, 4));

		Tuple2<FlatConstraints, FlatConstraints> parts = store.split(
				Collections.<LVar<?>> singletonList((LVar<?>) x.asVar().get()));
		assertThat(parts._1.getValue(x).get()).isEqualTo(FlatSet.of(1, 2));
		assertThat(parts._1.getValue(y).isDefined()).isFalse();
		assertThat(parts._2.getValue(y).get()).isEqualTo(FlatSet.of(3, 4));
		assertThat(parts._1.meet(parts._2)).isEqualTo(store);

		// canonical rename makes structurally equal stores compare equal cross-lineage
		Unifiable<Integer> z = lvar();
		FlatConstraints a = FlatConstraints.empty().withValue(x, FlatSet.of(5, 6));
		FlatConstraints b = FlatConstraints.empty().withValue(z, FlatSet.of(5, 6));
		assertThat(a.rename(Renaming.canonical(Collections.<LVar<?>> singletonList((LVar<?>) x.asVar().get()))))
				.isEqualTo(b.rename(Renaming.canonical(Collections.<LVar<?>> singletonList((LVar<?>) z.asVar().get()))));
	}
}
