package com.tgac.logic.tabling;

// ABOUTME: Pins TCLP stage 1: tabled calls under FD domains — residues key the
// ABOUTME: cache, the master runs FROM the key, consumers filter by their own state.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgac.logic.finitedomain.FiniteDomain;
import com.tgac.logic.finitedomain.domains.Arithmetic;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.Domain;
import io.vavr.collection.Array;
import com.tgac.logic.goals.Conde;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import io.vavr.Tuple4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class TabledUnderDomainsTest {

	private static Domain<Integer> dom(int... values) {
		return EnumeratedDomain.of(Array.ofAll(Arrays.stream(values).boxed())
				.map(Arithmetic::ofCasted));
	}

	/** The one-to-five generator: five ground disjuncts. */
	private static Tabled<Tuple1<Unifiable<Integer>>> oneToFive() {
		return Tabling.define(args -> args.apply(x -> Conde.of(Arrays.asList(
				unify(x, lval(1)), unify(x, lval(2)), unify(x, lval(3)),
				unify(x, lval(4)), unify(x, lval(5))))));
	}

	/** All pairs over 1..3 — nine ground disjuncts. */
	private static Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> grid() {
		return Tabling.define(args -> args.apply((a, b) -> {
			List<Goal> pairs = new ArrayList<>();
			for (int i = 1; i <= 3; i++) {
				for (int j = 1; j <= 3; j++) {
					pairs.add(unify(a, lval(i)).and(unify(b, lval(j))));
				}
			}
			return Conde.of(pairs);
		}));
	}

	@Test
	public void aTabledCallUnderADomainRuns() {
		// before stage 1 this threw "constraint"; now the domain keys the cache
		Tabled<Tuple1<Unifiable<Integer>>> gen = oneToFive();
		Unifiable<Integer> x = lvar();

		List<Integer> values = FiniteDomain.dom(x, dom(1, 2, 3))
				.and(gen.apply(Tuple.of(x)))
				.solve(x)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());

		assertThat(values).containsExactly(1, 2, 3);
	}

	@Test
	public void theMasterRunsFromTheKeyNotFromTheFirstCaller() {
		// caller 1's x+y=4 coupling is CARRIED into its key (stage 2), so the
		// two callers hold DIFFERENT keys: caller 1's master searches the
		// coupled region (3 pairs), caller 2's the rectangle (9). This also
		// pins the sealed-subsumer gate: caller 1's narrower entry must never
		// serve caller 2 — a constrained entry under-states its term pattern.
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> p = grid();
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lvar();

		Goal caller1 = FiniteDomain.dom(x, dom(1, 2, 3))
				.and(FiniteDomain.dom(y, dom(1, 2, 3)))
				.and(FiniteDomain.addo(x, y, lval(4)))
				.and(p.apply(Tuple.of(x, y)));
		Goal caller2 = FiniteDomain.dom(u, dom(1, 2, 3))
				.and(FiniteDomain.dom(v, dom(1, 2, 3)))
				.and(p.apply(Tuple.of(u, v)));

		Unifiable<Tuple4<Unifiable<Integer>, Unifiable<Integer>, Unifiable<Integer>, Unifiable<Integer>>> out =
				lval(Tuple.of(x, y, u, v));
		long combos = caller1.and(caller2).solve(out).count();

		// caller 1 filters to its three sum-4 pairs; caller 2 gets all nine
		assertThat(combos).isEqualTo(3L * 9L);
	}

	@Test
	public void differentDomainsAreDifferentVariants() {
		// {1,2} and {2,3} must NOT share an entry: if they collided, the second
		// caller would consume a cache computed under the first's region and
		// silently lose 3
		Tabled<Tuple1<Unifiable<Integer>>> gen = oneToFive();
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> u = lvar();

		Goal caller1 = FiniteDomain.dom(x, dom(1, 2)).and(gen.apply(Tuple.of(x)));
		Goal caller2 = FiniteDomain.dom(u, dom(2, 3)).and(gen.apply(Tuple.of(u)));

		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> out = lval(Tuple.of(x, u));
		long combos = caller1.and(caller2).solve(out).count();

		assertThat(combos).isEqualTo(2L * 2L);
	}

	@Test
	public void groundCallsUnderADomainStoreStayConstraintFree() {
		// knowledge about a GROUND arg is a constant, not knowledge: the call
		// keys constraint-free even though the FD store is non-empty (the
		// singleton domain collapsed to a binding, leaving a STALE entry — the
		// discharged answer-gate must wave it through)
		Tabled<Tuple1<Unifiable<Integer>>> gen = oneToFive();
		Unifiable<Integer> other = lvar();
		Unifiable<Integer> x = lvar();

		List<Integer> values = FiniteDomain.dom(other, dom(7))
				.and(unify(x, lval(2)))
				.and(gen.apply(Tuple.of(x)))
				.solve(x)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());

		assertThat(values).containsExactly(2);
	}

	@Test
	public void aDomainOnlyBodyBecomesAGenerator() {
		// stage 2: the body's live domain rides the answer as a residue and
		// replays at consumption — reify-time labelling enumerates it
		Tabled<Tuple1<Unifiable<Integer>>> vague =
				Tabling.define(args -> args.apply(x ->
						FiniteDomain.dom(x, dom(1, 2, 3))));
		Unifiable<Integer> x = lvar();

		List<Integer> values = vague.apply(Tuple.of(x))
				.solve(x)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(1, 2, 3);
	}

	@Test
	public void aCoupledAnswerCarriesItsConstraint() {
		// domains AND the addo ride the answer; consumption replays both and
		// labelling enumerates exactly the coupled region
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> region =
				Tabling.define(args -> args.apply((a, b) ->
						FiniteDomain.dom(a, dom(1, 2, 3))
								.and(FiniteDomain.dom(b, dom(1, 2, 3)))
								.and(FiniteDomain.addo(a, b, lval(4)))));
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		long pairs = region.apply(Tuple.of(x, y))
				.solve(lval(Tuple.of(x, y)))
				.count();
		assertThat(pairs).isEqualTo(3);   // (1,3) (2,2) (3,1)
	}

	@Test
	public void consumersFilterConditionalAnswers() {
		// the consumer's own narrower domain meets the replayed residue
		Tabled<Tuple1<Unifiable<Integer>>> vague =
				Tabling.define(args -> args.apply(x ->
						FiniteDomain.dom(x, dom(1, 2, 3))));
		Unifiable<Integer> x = lvar();

		List<Integer> values = FiniteDomain.dom(x, dom(2, 3))
				.and(vague.apply(Tuple.of(x)))
				.solve(x)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(2, 3);
	}

	@Test
	public void narrowerRedundantAnswersDedupByEntailment() {
		// two derivations of the SAME hole-term: the narrower residue is
		// entailed by the wider and must not replay a second time
		Tabled<Tuple1<Unifiable<Integer>>> gen =
				Tabling.define(args -> args.apply(x ->
						FiniteDomain.dom(x, dom(1, 2, 3))
								.or(FiniteDomain.dom(x, dom(1, 2)))));
		Unifiable<Integer> x = lvar();

		List<Integer> values = gen.apply(Tuple.of(x))
				.solve(x)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(1, 2, 3);
	}

	@Test
	public void aCouplingThroughALocalCarriesTheLocalAsAWitness() {
		// the body couples its arg to a local it never grounds: the WHOLE
		// delta rides the answer — the local replays as an existential (a
		// fresh var per consumption) and labelling enumerates exactly the
		// projection of the coupled region onto the arg
		Tabled<Tuple1<Unifiable<Integer>>> throughLocal =
				Tabling.define(args -> args.apply(x -> {
					Unifiable<Integer> w = lvar();
					return FiniteDomain.dom(x, dom(1, 2, 3))
							.and(FiniteDomain.dom(w, dom(1, 2, 3)))
							.and(FiniteDomain.addo(x, w, lval(4)));
				}));
		Unifiable<Integer> x = lvar();

		List<Integer> values = throughLocal.apply(Tuple.of(x))
				.solve(x)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(values).containsExactly(1, 2, 3);   // ∃w∈{1,2,3}: x+w=4
	}

	@Test
	public void twoArgsCoupledThroughALocalStayCoupled() {
		// x,y ∈ {1,2}, w ∈ {2,3}, x+y=w with only (x,y) as args: the carried
		// witness keeps the coupling — (2,2) must die (2+2=4 ∉ {2,3})
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel =
				Tabling.define(args -> args.apply((x, y) -> {
					Unifiable<Integer> w = lvar();
					return FiniteDomain.dom(x, dom(1, 2))
							.and(FiniteDomain.dom(y, dom(1, 2)))
							.and(FiniteDomain.dom(w, dom(2, 3)))
							.and(FiniteDomain.addo(x, y, w));
				}));
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		List<String> pairs = rel.apply(Tuple.of(x, y))
				.solve(lval(Tuple.of(x, y)))
				.map(Object::toString)
				.sorted()
				.collect(Collectors.toList());
		assertThat(pairs).containsExactly("{({1}, {1})}", "{({1}, {2})}", "{({2}, {1})}");
	}

	@Test
	public void anUnsatisfiableIslandKillsTheAnswer() {
		// a live island the propagators cannot refute (three vars, two
		// values, pairwise ≠ — locally consistent, pigeonhole-dead) rides
		// the answer and the consumer's labelling kills the emission: the
		// tabled goal emits exactly what the untabled goal does — nothing
		Tabled<Tuple1<Unifiable<Integer>>> withIsland =
				Tabling.define(args -> args.apply(x -> {
					Unifiable<Integer> z1 = lvar();
					Unifiable<Integer> z2 = lvar();
					Unifiable<Integer> z3 = lvar();
					return FiniteDomain.dom(x, dom(1, 2))
							.and(FiniteDomain.dom(z1, dom(1, 2)))
							.and(FiniteDomain.dom(z2, dom(1, 2)))
							.and(FiniteDomain.dom(z3, dom(1, 2)))
							.and(FiniteDomain.separate(z1, z2))
							.and(FiniteDomain.separate(z2, z3))
							.and(FiniteDomain.separate(z1, z3));
				}));
		Unifiable<Integer> x = lvar();

		assertThat(withIsland.apply(Tuple.of(x)).solve(x).count()).isEqualTo(0);
	}

	@Test
	public void twoConsumptionsOfACoupledAnswerAreIndependent() {
		// a conditional answer is a SCHEMA over its holes: each consumption
		// replays a fresh instance of the carried coupling, so consuming the
		// same answer twice yields the full product, never just the diagonal
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> region =
				Tabling.define(args -> args.apply((a, b) ->
						FiniteDomain.dom(a, dom(1, 2, 3))
								.and(FiniteDomain.dom(b, dom(1, 2, 3)))
								.and(FiniteDomain.addo(a, b, lval(4)))));
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();
		Unifiable<Integer> u = lvar();
		Unifiable<Integer> v = lvar();

		long quads = region.apply(Tuple.of(x, y))
				.and(region.apply(Tuple.of(u, v)))
				.solve(lval(Tuple.of(x, y, u, v)))
				.count();
		assertThat(quads).isEqualTo(9);   // the coupled line (1,3)(2,2)(3,1), squared
	}

	@Test
	public void twoConsumptionsOfADomainOnlyAnswerAreIndependent() {
		Tabled<Tuple1<Unifiable<Integer>>> gen =
				Tabling.define(args -> args.apply(a ->
						FiniteDomain.dom(a, dom(1, 2))));
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		long pairs = gen.apply(Tuple.of(x))
				.and(gen.apply(Tuple.of(y)))
				.solve(lval(Tuple.of(x, y)))
				.count();
		assertThat(pairs).isEqualTo(4);
	}

	@Test(timeout = 10_000)
	public void recursionUnderACarriedCouplingSharesItsEntry() {
		// the master seeds its body from the key by re-activating the carried
		// object ITSELF (an identity renaming yields the identical instance);
		// the recursive call projects that same object, its key lands in the
		// same entry, and the search terminates instead of minting entries
		Tabled<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> rel =
				Tabling.defineRecursive(self -> args -> args.apply((a, b) ->
						unify(a, lval(1)).and(unify(b, lval(3)))
								.or(Goal.defer(() -> self.apply(Tuple.of(a, b))))));
		Unifiable<Integer> x = lvar();
		Unifiable<Integer> y = lvar();

		long answers = FiniteDomain.dom(x, dom(1, 2, 3))
				.and(FiniteDomain.dom(y, dom(1, 2, 3)))
				.and(FiniteDomain.addo(x, y, lval(4)))
				.and(rel.apply(Tuple.of(x, y)))
				.solve(lval(Tuple.of(x, y)))
				.count();
		assertThat(answers).isEqualTo(1);   // (1,3)
	}
}
