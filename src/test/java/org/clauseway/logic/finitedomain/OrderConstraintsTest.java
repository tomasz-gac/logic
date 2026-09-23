package org.clauseway.logic.finitedomain;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.goals.Goal.success;
import static org.clauseway.logic.goals.Matche.llist;
import static org.clauseway.logic.unification.LVal.lval;
import static org.clauseway.logic.unification.LVar.lvar;

import org.clauseway.functional.Streams;
import org.clauseway.logic.Utils;
import org.clauseway.functional.monad.Cont;
import org.clauseway.logic.constraints.Constraints;
import org.clauseway.logic.constraints.Posting;
import org.clauseway.logic.goals.Package;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Matche;
import org.clauseway.logic.unification.LList;
import org.clauseway.logic.unification.Term;
import org.clauseway.logic.unification.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class OrderConstraintsTest {

	@Test
	public void leqIsCompleteAtTheBoundary() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		// completeness, not just soundness: the boundary pair (2,2) must be found
		List<Tuple2<Long, Long>> result =
				Utils.collect(Goal.success()
						.and(dom(i, Longs.range(1, 3)))
						.and(dom(j, Longs.range(1, 3)))
						.and(Longs.leq(i, j))
						.solve(lval(Tuple.of(i, j)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t.map1(Term::get).map2(Term::get)));

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(
						Tuple.of(1L, 1L),
						Tuple.of(1L, 2L),
						Tuple.of(2L, 2L));
	}

	@Test
	public void shouldConstrainAsLeq() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		List<Tuple2<Long, Long>> result =
				Utils.collect(Goal.success()
						.and((Longs.leq(i, j)))
						.and(dom(i, Longs.range(0, 4)))
						.and(dom(j, Longs.range(0, 4)))
						.solve(lval(Tuple.of(i, j)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t.map1(Term::get).map2(Term::get)));

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLeq2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		List<Tuple2<Long, Long>> result =
				Utils.collect(Goal.success()
						.and(dom(i, Longs.range(0, 4)))
						.and(dom(j, Longs.range(0, 4)))
						.and((Longs.leq(i, j)))
						.solve(lval(Tuple.of(i, j)), TestSchedulers.factory())
						.map(Term::get)
						.map(t -> t.map1(Term::get).map2(Term::get)));

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLeqThanNumber() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();

		List<Tuple2<Long, Long>> results = Utils.collect(Goal.success()
				.and(dom(x, Longs.range(3, 6)))
				.and(dom(z, Longs.range(3, 6)))
				.and(dom(y, Longs.range(1, 5)))
				.and(Longs.leq(x, lval(5L)))
				.and(Constraints.unify(x, y))
				.solve(lval(Tuple.of(y, z)), TestSchedulers.factory())
				.map(Term::get)
				.map(t -> t.map(Term::get, Term::get)));

		Assertions.assertThat(results)
				.allMatch(t -> t._1 <= 5 && t._2 <= 5);
	}

	@Test
	public void shouldAssureLessTransitive() {
		Unifiable<Integer> v0 = lvar();
		Unifiable<Integer> v1 = lvar();
		Unifiable<Integer> v2 = lvar();
		Unifiable<Integer> v3 = lvar();
		Unifiable<Integer> v4 = lvar();
		Unifiable<Integer> v5 = lvar();
		int n = 6;

		Unifiable<LList<Integer>> lst = LList.ofAll(v0, v1, v2, v3, v4, v5);
		var result = Utils.collect(allLesso(lst)
				.and(dom(v0, Ints.interval(0, n)))
				.and(dom(v1, Ints.interval(0, n)))
				.and(dom(v2, Ints.interval(0, n)))
				.and(dom(v3, Ints.interval(0, n)))
				.and(dom(v4, Ints.interval(0, n)))
				.and(dom(v5, Ints.interval(0, n)))
				.solve(lst, TestSchedulers.factory())
				.map(Term::get)
				.map(LList::toValueStream)
				.map(s -> s.collect(Collectors.toList())));

		HashSet<List<Integer>> unique = new HashSet<>(result);
		Assertions.assertThat(result)
				.hasSameElementsAs(unique)
				.allMatch(l ->
						Streams.zip(l.stream(), l.stream().skip(1), Tuple::of)
								.allMatch(lr -> lr.apply((lv, rv) -> lv < rv)));
	}

	public static Goal allLesso(Unifiable<LList<Integer>> lst) {
		return Matche.matche(lst,
				llist(() -> success()),
				llist((a) -> success()),
				llist((a, b, d) ->
						Ints.lss(a, b).and(defer(() -> allLesso(LList.of(b, d))))));
	}

	@Test
	public void geqGroundHoldsWhenMoreExceedsLess() {
		// geq(more, less) means more >= less; ground both ways
		Assertions.assertThat(Longs.geq(lval(480L), lval(400L)).solve(lvar(), TestSchedulers.factory()).count())
				.isEqualTo(1L);
		Assertions.assertThat(Longs.geq(lval(250L), lval(400L)).solve(lvar(), TestSchedulers.factory()).count())
				.isEqualTo(0L);
	}

	@Test
	public void geqBackwardsNarrowsToTheUpperTail() {
		// geq(x, 400) over [398,403) keeps {400, 401, 402} — the values >= 400
		Unifiable<Long> x = lvar();
		List<Long> xs = dom(x, Longs.range(398, 403))
				.and(Longs.geq(x, lval(400L)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());

		Assertions.assertThat(xs).containsExactly(400L, 401L, 402L);
	}

	@Test
	public void lssIsOneSharpAtom() {
		// strict order as a single propagator: one landing, one region delta —
		// not the leq-and-separate composition (two atoms, two wakes)
		Unifiable<Long> x = lvar();
		Posting posting = Longs.lss(x, lval(3L));
		Assertions.assertThat(posting).isInstanceOf(Posting.Activation.class);
		Assertions.assertThat(((Posting.Activation) posting).getItem().name()).isEqualTo("lss");
		Posting flipped = Longs.gtr(x, lval(3L));
		Assertions.assertThat(flipped).isInstanceOf(Posting.Activation.class);
		Assertions.assertThat(((Posting.Activation) flipped).getItem().name()).isEqualTo("lss");
	}

	@Test
	public void varVarLssNarrowsStrictBoundsBeforeLabelling() {
		// x < y over two open domains: sharp bounds give x <= max(y)-1 and
		// y >= min(x)+1 immediately — the composition's separate stays parked
		// on two vars and sheds nothing until grounding
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Package[] captured = new Package[1];
		Goal probe = s -> {
			captured[0] = s;
			return Cont.just(s);
		};
		long answers = dom(x, Longs.range(1, 5))
				.and(dom(y, Longs.range(1, 5)))
				.and(Longs.lss(x, y))
				.and(probe)
				.solve(x, TestSchedulers.factory())
				.count();
		Assertions.assertThat(answers).isGreaterThan(0);
		Assertions.assertThat(FiniteDomainConstraints.getDom(captured[0], captured[0].walk(x))
						.map(d -> d.stream().collect(java.util.stream.Collectors.toList()))
						.getOrNull())
				.containsExactly(1L, 2L, 3L);
		Assertions.assertThat(FiniteDomainConstraints.getDom(captured[0], captured[0].walk(y))
						.map(d -> d.stream().collect(java.util.stream.Collectors.toList()))
						.getOrNull())
				.containsExactly(2L, 3L, 4L);
	}

	@Test
	public void gtrGroundIsStrict() {
		Assertions.assertThat(Longs.gtr(lval(401L), lval(400L)).solve(lvar(), TestSchedulers.factory()).count())
				.isEqualTo(1L);
		Assertions.assertThat(Longs.gtr(lval(400L), lval(400L)).solve(lvar(), TestSchedulers.factory()).count())
				.isEqualTo(0L);
	}

	@Test
	public void gtrBackwardsNarrowsStrictly() {
		// gtr(x, 400) over [398,403) keeps {401, 402}
		Unifiable<Long> x = lvar();
		List<Long> xs = dom(x, Longs.range(398, 403))
				.and(Longs.gtr(x, lval(400L)))
				.solve(x, TestSchedulers.factory())
				.map(Term::get)
				.sorted()
				.collect(Collectors.toList());

		Assertions.assertThat(xs).containsExactly(401L, 402L);
	}

}
