package com.tgac.logic.lattice;

// ABOUTME: Proves the lattice store is generic: a flat set-of-values instance gets
// ABOUTME: verification, narrowing, collapse, propagators, split and rename for free.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.constraints.Propagation;
import com.tgac.logic.goals.Package;
import com.tgac.logic.constraints.store.Renaming;
import com.tgac.logic.constraints.store.Theory;
import com.tgac.logic.unification.Any;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Name;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.control.Option;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import org.junit.Test;

/**
 * The second store instance (docs/design/lattice-store.md §2): a flat lattice of
 * arbitrary values — meet is intersection, a point is a size-one set. Everything
 * asserted here (ground verification, narrowing, collapse to an inferred
 * binding, named propagators waking on their terms, split and canonical rename)
 * is inherited machinery; the component lattice carries its capability record
 * and the store supplies only its {@code enforce}.
 */
public class LatticeFactorTest {

	/** The component lattice: a finite set of admissible values. */
	@Value
	static class FlatSet implements Domain<FlatSet> {
		@Getter
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

	/** The store behavior: stateless, nothing but its {@code enforce}. */
	static final class FlatConstraints extends LatticeFactor<FlatSet, FlatConstraints> {
		private static final FlatConstraints EMPTY = new FlatConstraints();

		private FlatConstraints() {
		}

		static FlatConstraints empty() {
			return EMPTY;
		}

		@Override
		public <T> Goal enforce(Term<T> x) {
			return Goal.success();
		}
	}

	/** A one-entry theory: {@code target ⊂ values}. */
	static Theory<FlatConstraints> valued(Term<?> target, Object... values) {
		return Theory.of(Collections.singletonList(
				new Imposition<>(FlatConstraints.class, target, FlatSet.of(values),
						FlatConstraints.empty())));
	}

	@Test
	public void aGroundKeyedEntryVerifiesAtNormalize() {
		// a ground-keyed imposition can enter through the theory crossing
		// (Imposition.rename keeps val-resolved targets); normalize must
		// verify it against the domain, not skip it as live-at-root
		Theory<FlatConstraints> inadmissible = valued(lval(5), 1, 2);
		boolean failed = new BreadthFirstScheduler<>(
				FlatConstraints.empty().normalize(inadmissible, inadmissible.atoms(), Package.empty()))
				.get()
				.match(() -> true, () -> false, upd -> false);
		assertThat(failed).isTrue();

		Theory<FlatConstraints> admissible = valued(lval(1), 1, 2);
		Theory<FlatConstraints> spent = new BreadthFirstScheduler<>(
				FlatConstraints.empty().normalize(admissible, admissible.atoms(), Package.empty()))
				.get()
				.<Theory<FlatConstraints>> match(() -> null, () -> null,
						upd -> (Theory<FlatConstraints>) upd.constraint().getTheory());
		assertThat(spent).isNotNull();
		assertThat(spent.isEmpty()).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void theTheoryCrossesWholeThroughTheDoorMeet() {
		// the crossing there and back is pure value work now: the resident
		// theory IS the knowledge, so the door's meet into an empty resident
		// reproduces it exactly — impositions and propagators alike
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Theory<FlatConstraints> original = valued((Term<?>) x, 1, 2)
				.with(Propagator.of(FlatConstraints.empty(), "even",
						Collections.<Term<?>> singletonList(y),
						(watched, state) -> Verdict.keep()));
		assertThat(Theory.<FlatConstraints> empty().meet(original))
				.isEqualTo(original);
	}

	private static Goal flat(Unifiable<?> x, FlatSet values) {
		return FlatConstraints.empty().impose(x, values);
	}

	@Test
	public void aGroundBindingIsVerifiedAgainstTheFlatValue() {
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2)).and(x.unifies(1)).solve(x, TestSchedulers.factory()).count())
				.isEqualTo(1);

		Unifiable<Integer> y = lvar();
		assertThat(flat(y, FlatSet.of(1, 2)).and(y.unifies(3)).solve(y, TestSchedulers.factory()).count())
				.isZero();
	}

	@Test
	public void meetNarrowsAndACollapseInfersTheBinding() {
		Unifiable<Integer> x = lvar();
		List<Integer> answers = flat(x, FlatSet.of(1, 2))
				.and(flat(x, FlatSet.of(2, 3)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(2);
	}

	@Test
	public void disjointImpositionsFail() {
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2)).and(flat(x, FlatSet.of(3, 4))).solve(x, TestSchedulers.factory()).count())
				.isZero();
	}

	@Test
	public void aNamedPropagatorWakesOnItsWatchedTerm() {
		Unifiable<Integer> x = lvar();
		assertThat(flat(x, FlatSet.of(1, 2, 3, 4)).and(evenO(x)).and(x.unifies(4)).solve(x, TestSchedulers.factory()).count())
				.isEqualTo(1);

		Unifiable<Integer> y = lvar();
		assertThat(flat(y, FlatSet.of(1, 2, 3, 4)).and(evenO(y)).and(y.unifies(3)).solve(y, TestSchedulers.factory()).count())
				.isZero();
	}

	/** A parked constraint: once its variable grounds, even passes, odd fails. */
	private static Goal evenO(Unifiable<Integer> x) {
		return Propagation.activate(
				Propagator.of(FlatConstraints.empty(), "even",
						Collections.<Term<?>> singletonList(x),
						(watched, pkg) -> {
							Term<?> w = pkg.walk(watched.get(0));
							if (!w.isVal()) {
								return Verdict.keep();
							}
							return ((Integer) w.get()) % 2 == 0 ? Verdict.subsumed() : Verdict.fail();
						}));
	}

	@Test
	public void splitPartitionsByNameAndRenameReKeys() {
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Theory<FlatConstraints> theory = valued((Term<?>) x, 1, 2)
				.meet(valued((Term<?>) y, 3, 4));

		Tuple2<Theory<FlatConstraints>, Theory<FlatConstraints>> parts = theory.split(
				Collections.<LVar<?>> singletonList((LVar<?>) x.asVar().get()));
		assertThat(parts._1.atoms()).allMatch(a -> a.watched().contains(x.getObjectTerm()));
		assertThat(((Imposition<?, ?>) parts._1.atoms().head()).getValue()).isEqualTo(FlatSet.of(1, 2));
		assertThat(((Imposition<?, ?>) parts._2.atoms().head()).getValue()).isEqualTo(FlatSet.of(3, 4));
		assertThat(parts._1.meet(parts._2)).isEqualTo(theory);

		// canonical rename makes structurally equal theories compare equal cross-lineage
		Unifiable<Integer> z = lvar();
		Theory<FlatConstraints> a = valued((Term<?>) x, 5, 6);
		Theory<FlatConstraints> b = valued((Term<?>) z, 5, 6);
		assertThat(a.rename(Renaming.of(Collections.<Name<?>, Term<?>> singletonMap(x.asVar().get(), Any.of(0)))).ground())
				.isEqualTo(b.rename(Renaming.of(Collections.<Name<?>, Term<?>> singletonMap(z.asVar().get(), Any.of(0)))).ground());
	}
}
