package com.tgac.logic.nogoods;

// ABOUTME: Nogoods through the tabling machinery: caller nogoods key the call,
// ABOUTME: equal keys share, body locals ride as witnesses, recursion carries them.

import com.tgac.logic.TestSchedulers;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.tabling.Tabled;
import com.tgac.logic.tabling.Tabling;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class NogoodsUnderTablingTest {

	private static Tabled<Unifiable<Long>> zeroToFour() {
		return Tabling.define(x -> dom(x, EnumeratedDomain.range(0L, 5L)));
	}

	@Test
	public void aCallerNogoodEntersTheKey() {
		// caller 1's ¬(x=3) is caller-private knowledge: if it failed to key,
		// one caller would consume the other's cache — filtered answers
		// leaking to caller 2, or the full set to caller 1
		Tabled<Unifiable<Long>> gen = zeroToFour();
		Unifiable<Long> x = lvar();
		Unifiable<Long> u = lvar();

		Goal caller1 = exclude(x.unifies(3L)).and(gen.apply(x));
		Goal caller2 = gen.apply(u);

		Unifiable<Tuple2<Unifiable<Long>, Unifiable<Long>>> out = lval(Tuple.of(x, u));
		long combos = caller1.and(caller2).solve(out, TestSchedulers.factory()).count();

		// caller 1 labels to {0,1,2,4}; caller 2 to all five
		assertThat(combos).isEqualTo(4L * 5L);
	}

	@Test
	public void equalNogoodsAcrossLineagesStayCorrect() {
		// two callers with structurally equal nogoods on their own vars: keys
		// match cross-lineage and both consume the same filtered region
		Tabled<Unifiable<Long>> gen = zeroToFour();
		Unifiable<Long> x = lvar();
		Unifiable<Long> u = lvar();

		Goal caller1 = exclude(x.unifies(3L)).and(gen.apply(x));
		Goal caller2 = exclude(u.unifies(3L)).and(gen.apply(u));

		Unifiable<Tuple2<Unifiable<Long>, Unifiable<Long>>> out = lval(Tuple.of(x, u));
		long combos = caller1.and(caller2).solve(out, TestSchedulers.factory()).count();

		assertThat(combos).isEqualTo(4L * 4L);
	}

	@Test
	public void aBodyLocalNogoodRidesAsAWitness() {
		// the body forbids x = w for a local w the caller never sees; w's
		// binding rides the answer delta, so the exclusion lands on 2
		Tabled<Unifiable<Long>> notTheLocal = Tabling.define(x -> {
			Unifiable<Long> w = lvar();
			return dom(x, EnumeratedDomain.range(1L, 4L))
					.and(dom(w, EnumeratedDomain.range(2L, 3L)))
					.and(exclude(x.unifies(w)));
		});
		Unifiable<Long> x = lvar();

		List<Long> values = notTheLocal.apply(x)
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());

		assertThat(values).containsExactly(1L, 3L);
	}

	@Test
	public void bothViolationOrdersFail() {
		Tabled<Unifiable<Long>> notThree =
				Tabling.define(x -> exclude(x.unifies(3L)));

		// bind before the call: the ground call's master is born violated
		Unifiable<Long> y = lvar();
		assertThat(y.unifies(3L).and(notThree.apply(y))
				.solve(y, TestSchedulers.factory()).count()).isZero();

		// bind after the call: the replayed nogood vetoes at the caller
		Unifiable<Long> z = lvar();
		assertThat(notThree.apply(z).and(z.unifies(3L))
				.solve(z, TestSchedulers.factory()).count()).isZero();
	}

	@Test
	public void aCallerSideNogoodFiltersTabledAnswers() {
		Tabled<Unifiable<Long>> gen = zeroToFour();
		Unifiable<Long> y = lvar();

		List<Long> values = gen.apply(y).and(exclude(y.unifies(1L)))
				.solve(y, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());

		assertThat(values).containsExactly(0L, 2L, 3L, 4L);
	}

	private Goal parent(Unifiable<String> x, Unifiable<String> y) {
		return x.unifies("alice").and(y.unifies("bob"))
				.or(x.unifies("bob").and(y.unifies("charlie")))
				.or(x.unifies("charlie").and(y.unifies("david")));
	}

	private final Tabled<Tuple2<Unifiable<String>, Unifiable<String>>> ancestorButNotBob =
			Tabling.define(args -> args.apply((x, y) ->
					exclude(y.unifies("bob"))
							.and(parent(x, y)
									.or(defer(() -> {
										Unifiable<String> z = lvar();
										return parent(x, z)
												.and(ancestorButNotBob(z, y));
									})))));

	private Goal ancestorButNotBob(Unifiable<String> x, Unifiable<String> y) {
		return ancestorButNotBob.apply(Tuple.of(x, y));
	}

	@Test
	public void recursionCarriesTheNogoodThroughEveryEntry() {
		// the exclusion rides the recursive calls' keys and every answer's
		// delta: alice's descendants minus bob
		Unifiable<String> who = lvar();

		List<String> values = ancestorButNotBob(lval("alice"), who)
				.solve(who, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());

		assertThat(values).containsExactly("charlie", "david");
	}
}
