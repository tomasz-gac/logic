package com.tgac.logic.tabling;

// ABOUTME: Pins tabled calls under disequality: neq residues key the cache, ride
// ABOUTME: answers, and replay by copy — canonical across caller lineages.

import static com.tgac.logic.constraints.Constraints.unify;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.goals.Package;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import java.util.stream.Collectors;
import java.util.List;
import org.junit.Test;

public class TabledUnderNeqTest {

	/** The one-or-two generator. */
	private static Tabled<Tuple1<Unifiable<Integer>>> oneOrTwo() {
		return Tabling.define(args -> args.apply(x ->
				unify(x, lval(1)).or(unify(x, lval(2)))));
	}

	@Test
	public void aNeqBodyBecomesAConditionalAnswer() {
		// the answer is _.0 GIVEN _.0 ≠ 1; consumption restates the record
		// and the consumer's own binding meets it
		Tabled<Tuple1<Unifiable<Integer>>> notOne =
				Tabling.define(args -> args.apply(x ->
						Disequality.separate(x, lval(1))));

		Unifiable<Integer> y = lvar();
		assertThat(notOne.apply(Tuple.of(y))
				.and(unify(y, lval(2)))
				.solve(y)
				.count()).isEqualTo(1);

		Unifiable<Integer> z = lvar();
		assertThat(notOne.apply(Tuple.of(z))
				.and(unify(z, lval(1)))
				.solve(z)
				.count()).isEqualTo(0);
	}

	@Test
	public void callerNeqContextKeysTheCallAndSeedsTheMaster() {
		// the constrained call's master runs FROM THE KEY (≠2 restated), so
		// its cache holds only 1; the unconstrained call is its own entry
		Tabled<Tuple1<Unifiable<Integer>>> gen = oneOrTwo();
		Package p = Package.empty().withStore(Table.empty());

		Unifiable<Integer> x = lvar();
		List<Integer> constrained = Disequality.separate(x, lval(2))
				.and(gen.apply(Tuple.of(x)))
				.solveFrom(p, x, BreadthFirstScheduler::new)
				.map(Term::<Integer> get)
				.collect(Collectors.toList());
		assertThat(constrained).containsExactly(1);

		Unifiable<Integer> z = lvar();
		List<Integer> free = gen.apply(Tuple.of(z))
				.solveFrom(p, z, BreadthFirstScheduler::new)
				.map(Term::<Integer> get)
				.sorted()
				.collect(Collectors.toList());
		assertThat(free).containsExactly(1, 2);
		assertThat(p.getStore(Table.class).entries()).hasSize(2);
	}

	@Test
	public void sameShapedContextsShareOneEntry() {
		// transcribed records are canonical: two INDEPENDENT callers under a
		// same-shaped disequality produce the same key and share the entry
		Tabled<Tuple1<Unifiable<Integer>>> gen = oneOrTwo();
		Package p = Package.empty().withStore(Table.empty());

		Unifiable<Integer> u = lvar();
		assertThat(Disequality.separate(u, lval(5))
				.and(gen.apply(Tuple.of(u)))
				.solveFrom(p, u, BreadthFirstScheduler::new)
				.count()).isEqualTo(2);

		Unifiable<Integer> v = lvar();
		assertThat(Disequality.separate(v, lval(5))
				.and(gen.apply(Tuple.of(v)))
				.solveFrom(p, v, BreadthFirstScheduler::new)
				.count()).isEqualTo(2);

		assertThat(p.getStore(Table.class).entries()).hasSize(1);
	}

	@Test
	public void twoConsumptionsOfANeqAnswerAreIndependent() {
		// records replay by COPY (data over slots), so two consumptions of
		// the same conditional answer never couple to each other
		Tabled<Tuple1<Unifiable<Integer>>> notOne =
				Tabling.define(args -> args.apply(x ->
						Disequality.separate(x, lval(1))));

		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		assertThat(notOne.apply(Tuple.of(a))
				.and(notOne.apply(Tuple.of(b)))
				.and(unify(a, lval(2)))
				.and(unify(b, lval(3)))
				.solve(lval(Tuple.of(a, b)))
				.count()).isEqualTo(1);
	}
}
