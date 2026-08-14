package com.tgac.logic.disjunction;

// ABOUTME: The disjunction store through the tabling machinery: disjuncts ride
// ABOUTME: answers as renamed factors, re-verify at the consumer, key the call.

import static com.tgac.logic.disjunction.Disjunction.any;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import static com.tgac.logic.unification.LVal.lval;

import com.tgac.logic.TestSchedulers;
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

public class DisjunctionUnderTablingTest {

	@Test
	public void aDisjunctRidesATabledAnswerAndDecidesAtTheConsumer() {
		// the tabled goal leaves x undecided under a disjunct; the consumer
		// binds x and the replayed disjunct discharges there
		Tabled<Unifiable<Integer>> constrained =
				Tabling.define(x -> any(x.unifies(1), x.unifies(2)));

		Unifiable<Integer> x = lvar();
		List<Integer> answers = constrained.apply(x)
				.and(x.unifies(2))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(2);
	}

	@Test
	public void anUndecidedRiddenDisjunctEnumeratesAtTheCallerGroundFloor() {
		// nothing decides it after replay: the caller's ground floor expands
		Tabled<Unifiable<Integer>> constrained =
				Tabling.define(x -> any(x.unifies(1), x.unifies(2)));

		Unifiable<Integer> x = lvar();
		List<Integer> answers = constrained.apply(x)
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(answers).containsExactly(1, 2);
	}

	@Test
	public void aCallerDisjunctEntersTheKey() {
		// caller 1's (x=0 ∨ x=1) is caller-private knowledge: if it failed
		// to key, one caller would consume the other's cache
		Tabled<Unifiable<Long>> gen =
				Tabling.define(x -> dom(x, EnumeratedDomain.range(0L, 5L)));
		Unifiable<Long> x = lvar();
		Unifiable<Long> u = lvar();

		Goal caller1 = any(x.unifies(0L), x.unifies(1L)).and(gen.apply(x));
		Goal caller2 = gen.apply(u);

		Unifiable<Tuple2<Unifiable<Long>, Unifiable<Long>>> out = lval(Tuple.of(x, u));
		long combos = caller1.and(caller2).solve(out, TestSchedulers.factory()).count();

		// caller 1 labels to {0,1}; caller 2 to all five
		assertThat(combos).isEqualTo(10);
	}
}
