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
		// caller 1 privately couples x+y=4; caller 2 shares the KEY (same
		// domains) but not the coupling. The cache must hold the key's nine
		// pairs, not caller 1's three — else caller 2 is silently incomplete.
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
	public void anAnswerCarryingALiveDomainIsStillRefused() {
		// the body constrains its arg but never grounds it: the answer would
		// carry live knowledge — stage 2's territory, refused loudly
		Tabled<Tuple1<Unifiable<Integer>>> vague =
				Tabling.define(args -> args.apply(x ->
						FiniteDomain.dom(x, dom(1, 2, 3))));
		Unifiable<Integer> x = lvar();

		assertThatThrownBy(() -> vague.apply(Tuple.of(x)).solve(x).count())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("constraint");
	}
}
