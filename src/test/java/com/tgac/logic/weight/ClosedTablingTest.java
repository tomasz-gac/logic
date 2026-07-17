package com.tgac.logic.weight;

// ABOUTME: The closed (star) tabling path. Wait-mode explore seals like plain
// ABOUTME: tabling, drops escapes, and captures each answer's base weight on the entry.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.tgac.functional.algebra.BoundedSemiring;
import com.tgac.functional.algebra.ClosedSemiring;
import com.tgac.functional.algebra.Provenance;
import com.tgac.functional.algebra.Semirings;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Package;
import com.tgac.logic.tabling.Table;
import com.tgac.logic.tabling.TableEntry;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class ClosedTablingTest {

	/**
	 * Probability (+, ×) over [0,1] with Kleene closure {@code a* = 1/(1-a)} — the
	 * geometric series, valid for a &lt; 1. Kept test-local (see semiring-inference.md
	 * §6): the star is a real probability only when the derivations summed are
	 * mutually exclusive, as in the "first success at step k" encoding below.
	 */
	private static final ClosedSemiring<Double> PROB = new ClosedSemiring<Double>() {
		@Override
		public Double zero() {
			return 0.0;
		}

		@Override
		public Double one() {
			return 1.0;
		}

		@Override
		public Double plus(Double a, Double b) {
			return a + b;
		}

		@Override
		public Double times(Double a, Double b) {
			return a * b;
		}

		@Override
		public Double star(Double a) {
			return 1.0 / (1.0 - a);
		}
	};

	@Test
	public void closedEmitsBaseAnswersLikeBounded() {
		// a two-fact tabled relation, no recursion. solveBounded streams its answers;
		// solveClosed explores and seals the same way, and with no loop the star emits
		// each answer with its base weight — here the ⊗-identity 0, no factor.
		Tabled<Unifiable<String>> rel = Tabling.define(x ->
				unify(x, lval("a")).or(unify(x, lval("b"))));
		BoundedSemiring<SemiringStore> ring = SemiringStore.boundedProduct(Semirings.MIN_PLUS);

		Unifiable<String> streamed = lvar();
		assertThat(Weights.solveBounded(rel.apply(streamed), streamed, ring, BreadthFirstScheduler::new)
				.count()).isEqualTo(2);

		Unifiable<String> closed = lvar();
		List<Tuple2<Reified<String>, SemiringStore>> answers =
				Weights.solveClosed(rel.apply(closed), closed, ring, BreadthFirstScheduler::new)
						.collect(Collectors.toList());
		assertThat(answers).hasSize(2);
		assertThat(answers).allMatch(a -> a._2.get(Semirings.MIN_PLUS) == 0L);
	}

	@Test
	public void waitModeCapturesBaseWeightOnTheEntry() {
		// loop(1) :- factor(3) | factor(2), loop(1).  The non-looping branch (3) is
		// the base; the looping branch (2) parks on a sleeper. Only the 3 lands as a
		// base weight on the entry.
		Tabled<Tuple1<Unifiable<Integer>>> loop = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.MIN_PLUS, 3L))
						.or(unify(x, lval(1))
								.and(Weights.factor(Semirings.MIN_PLUS, 2L))
								.and(Goal.defer(() -> self.apply(t))))));
		BoundedSemiring<SemiringStore> ring = SemiringStore.boundedProduct(Semirings.MIN_PLUS);
		Table table = closedTable(ring);
		Unifiable<Integer> out = lvar();
		Package root = Package.empty().withStore(table).withStore(ring.one());

		loop.apply(Tuple.of(lval(1))).solveFrom(root, out, BreadthFirstScheduler::new).count();

		TableEntry<Object> entry = table.entries().iterator().next();
		assertThat(entry.baseWeights().values()).hasSize(1);
		SemiringStore base = (SemiringStore) entry.baseWeights().values().iterator().next();
		assertThat(base.get(Semirings.MIN_PLUS)).isEqualTo(3L);
	}

	@Test
	public void provenanceBaseIsCapturedOnTheEntry() {
		// loop(1) :- factor("base") | factor("step"), loop(1). Provenance shows the
		// capture legibly: the non-looping branch lands the base symbol "base", the
		// looping "step" stays on the sleeper.
		Tabled<Tuple1<Unifiable<Integer>>> loop = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("base")))
						.or(unify(x, lval(1))
								.and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("step")))
								.and(Goal.defer(() -> self.apply(t))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);
		Table table = closedTable(ring);
		Unifiable<Integer> out = lvar();
		loop.apply(Tuple.of(lval(1)))
				.solveFrom(Package.empty().withStore(table).withStore(ring.one()), out, BreadthFirstScheduler::new)
				.count();

		TableEntry<Object> entry = table.entries().iterator().next();
		assertThat(entry.baseWeights().values()).hasSize(1);
		SemiringStore base = (SemiringStore) entry.baseWeights().values().iterator().next();
		assertThat(base.get(Semirings.PROVENANCE).sameLanguage(Provenance.sym("base"), 3)).isTrue();
	}

	@Test
	public void provenanceCapturesABasePerAnswer() {
		// r(1) :- factor("a").  r(2) :- factor("b").  Two answers, two DISTINCT bases —
		// the answer->base map a one-answer relation collapses to a single number.
		Tabled<Tuple1<Unifiable<Integer>>> r = Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("a")))
						.or(unify(x, lval(2)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("b"))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);
		Table table = closedTable(ring);
		Unifiable<Integer> out = lvar();
		r.apply(Tuple.of(out))
				.solveFrom(Package.empty().withStore(table).withStore(ring.one()), out, BreadthFirstScheduler::new)
				.count();

		TableEntry<Object> entry = table.entries().iterator().next();
		List<String> bases = entry.baseWeights().values().stream()
				.map(v -> ((SemiringStore) v).get(Semirings.PROVENANCE).toString())
				.sorted()
				.collect(Collectors.toList());
		assertThat(bases).containsExactly("a", "b");
	}

	@Test
	public void provenanceCombinesTwoBasesForTheSameAnswer() {
		// r(1) :- factor("a") | factor("b").  ONE answer (x=1) reached two non-looping
		// ways — the base folds them by ⊕: base = a + b. The presence cell dedups the
		// KEY; the base map keeps both derivations' weights (capture is before dedup).
		Tabled<Tuple1<Unifiable<Integer>>> r = Tabling.define(t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("a")))
						.or(unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("b"))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);
		Table table = closedTable(ring);
		Unifiable<Integer> out = lvar();
		r.apply(Tuple.of(out))
				.solveFrom(Package.empty().withStore(table).withStore(ring.one()), out, BreadthFirstScheduler::new)
				.count();

		TableEntry<Object> entry = table.entries().iterator().next();
		assertThat(entry.baseWeights().values()).hasSize(1);
		SemiringStore base = (SemiringStore) entry.baseWeights().values().iterator().next();
		Provenance ab = Provenance.alt(Provenance.sym("a"), Provenance.sym("b"));
		assertThat(base.get(Semirings.PROVENANCE).sameLanguage(ab, 3)).isTrue();
	}

	@Test
	public void provenanceCoefficientIsCapturedOnTheEntry() {
		// loop(1) :- factor("base") | factor("step"), loop(1). The looping branch's
		// one-step weight "step" lands as the edge coefficient A[loop <- loop].
		Tabled<Tuple1<Unifiable<Integer>>> loop = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("base")))
						.or(unify(x, lval(1))
								.and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("step")))
								.and(Goal.defer(() -> self.apply(t))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);
		Table table = closedTable(ring);
		Unifiable<Integer> out = lvar();
		loop.apply(Tuple.of(lval(1)))
				.solveFrom(Package.empty().withStore(table).withStore(ring.one()), out, BreadthFirstScheduler::new)
				.count();

		TableEntry<Object> entry = table.entries().iterator().next();
		assertThat(entry.edges()).hasSize(1);
		SemiringStore coeff = (SemiringStore) entry.edges().values().iterator().next();
		assertThat(coeff.get(Semirings.PROVENANCE).sameLanguage(Provenance.sym("step"), 3)).isTrue();
	}

	@Test
	public void nonlinearRecursionThrows() {
		// nl(1) :- factor("b") | nl(1), nl(1).  the recursive branch consumes two loop
		// members in one derivation — outside star's reach, so it fails loudly.
		Tabled<Tuple1<Unifiable<Integer>>> nl = Tabling.defineRecursive(self -> t -> t.apply(x ->
				Weights.factor(Semirings.PROVENANCE, Provenance.sym("b"))
						.or(Goal.defer(() -> self.apply(t).and(self.apply(t))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);
		Unifiable<Integer> out = lvar();
		assertThatThrownBy(() ->
				Weights.solveClosed(nl.apply(Tuple.of(lval(1))), out, ring, BreadthFirstScheduler::new)
						.collect(Collectors.toList()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("nonlinear");
	}

	@Test
	public void starSolvesTheSelfLoopToItsRegex() {
		// loop(1) :- factor("base") | factor("step"), loop(1). After explore captures
		// base + coefficient, the star solves x = step* · base — every derivation.
		Tabled<Tuple1<Unifiable<Integer>>> loop = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("base")))
						.or(unify(x, lval(1))
								.and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("step")))
								.and(Goal.defer(() -> self.apply(t))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);
		Table table = closedTable(ring);
		loop.apply(Tuple.of(lval(1)))
				.solveFrom(Package.empty().withStore(table).withStore(ring.one()), lvar(), BreadthFirstScheduler::new)
				.count();

		TableEntry<Object> entry = table.entries().iterator().next();
		Map<Reified<?>, SemiringStore> solved = StarTabling.solve(entry, ring);
		assertThat(solved).hasSize(1);
		Provenance x = solved.values().iterator().next().get(Semirings.PROVENANCE);
		Provenance expected = Provenance.cat(Provenance.star(Provenance.sym("step")), Provenance.sym("base"));
		assertThat(x.sameLanguage(expected, 6)).isTrue();
	}

	@Test
	public void solveClosedEmitsTheSelfLoopStar() {
		// loop(1) :- factor("base") | factor("step"), loop(1). End to end: explore
		// seals, the star folds the loop, and emit delivers the single answer with
		// value step* · base to the collector.
		Tabled<Tuple1<Unifiable<Integer>>> loop = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("base")))
						.or(unify(x, lval(1))
								.and(Weights.factor(Semirings.PROVENANCE, Provenance.sym("step")))
								.and(Goal.defer(() -> self.apply(t))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.PROVENANCE);

		Unifiable<Integer> out = lvar();
		List<Tuple2<Reified<Integer>, SemiringStore>> answers =
				Weights.solveClosed(loop.apply(Tuple.of(lval(1))), out, ring, BreadthFirstScheduler::new)
						.collect(Collectors.toList());

		assertThat(answers).hasSize(1);
		Provenance x = answers.get(0)._2.get(Semirings.PROVENANCE);
		Provenance expected = Provenance.cat(Provenance.star(Provenance.sym("step")), Provenance.sym("base"));
		assertThat(x.sameLanguage(expected, 6)).isTrue();
	}

	@Test
	public void solveClosedEmitsLeftRecursionAnswers() {
		// path(1,Y) over the chain 1->2->3->4, each edge weight 1 (min-plus). The
		// base branch gives path(1,2); the recursive consumer manufactures the
		// farther answers 3 and 4, and the star chains the edges into lengths 1,2,3.
		class PathGoal {
			Goal edge(Unifiable<Integer> x, Unifiable<Integer> y) {
				return unify(x, lval(1)).and(unify(y, lval(2))).and(Weights.factor(Semirings.MIN_PLUS, 1L))
						.or(unify(x, lval(2)).and(unify(y, lval(3))).and(Weights.factor(Semirings.MIN_PLUS, 1L)))
						.or(unify(x, lval(3)).and(unify(y, lval(4))).and(Weights.factor(Semirings.MIN_PLUS, 1L)));
			}

			final Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> path =
					Tabling.defineRecursive(self -> args -> args.apply((x, y) ->
							edge(x, y)
									.or(Goal.defer(() -> {
										Unifiable<Integer> z = lvar();
										return self.apply(Tuple.of(x, z)).and(edge(z, y));
									}))));
		}
		PathGoal pg = new PathGoal();
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(Semirings.MIN_PLUS);
		Unifiable<Integer> y = lvar();

		List<Long> lengths = Weights.solveClosed(pg.path.apply(Tuple.of(lval(1), y)), y, ring, BreadthFirstScheduler::new)
				.map(t -> t._2.get(Semirings.MIN_PLUS))
				.sorted()
				.collect(Collectors.toList());

		assertThat(lengths).containsExactly(1L, 2L, 3L);
	}

	@Test
	public void solveClosedSumsAProbabilisticGeometricSeries() {
		// Roll one die repeatedly; P(ever roll a 6). The disjoint "first 6 at step k"
		// events give ever6 :- factor(1/6) | factor(5/6), ever6 — a self-loop with a
		// constant coefficient. The star sums the geometric series: 6 * (1/6) = 1.
		// (The growing "1, then 2, then 3, ..." version is outside the star: its loop
		// coefficient depends on the round, so there is no finite constant matrix.)
		Tabled<Tuple1<Unifiable<Integer>>> ever = Tabling.defineRecursive(self -> t -> t.apply(x ->
				unify(x, lval(1)).and(Weights.factor(PROB, 1.0 / 6))
						.or(unify(x, lval(1))
								.and(Weights.factor(PROB, 5.0 / 6))
								.and(Goal.defer(() -> self.apply(t))))));
		ClosedSemiring<SemiringStore> ring = SemiringStore.closedProduct(PROB);
		Unifiable<Integer> out = lvar();

		List<Tuple2<Reified<Integer>, SemiringStore>> answers =
				Weights.solveClosed(ever.apply(Tuple.of(lval(1))), out, ring, BreadthFirstScheduler::new)
						.collect(Collectors.toList());

		assertThat(answers).hasSize(1);
		assertThat((Double) answers.get(0)._2.get(PROB)).isCloseTo(1.0, within(1e-9));
	}

	@SuppressWarnings("unchecked")
	private static Table closedTable(ClosedSemiring<SemiringStore> ring) {
		return Table.closed(
				(ClosedSemiring<Object>) (ClosedSemiring<?>) ring,
				p -> p.getStores().get(SemiringStore.class).getOrElse(ring.one()),
				(p, v) -> p.putStore((SemiringStore) v),
				entry -> (Map<Reified<?>, Object>) (Map<Reified<?>, ?>) StarTabling.solve(entry, ring));
	}
}
