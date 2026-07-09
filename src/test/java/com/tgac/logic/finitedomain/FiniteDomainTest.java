package com.tgac.logic.finitedomain;

import static com.tgac.logic.Utils.collect;
import static com.tgac.logic.finitedomain.FiniteDomain.addo;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.separate;
import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.goals.Matche.llist;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.constraints.Constraints;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class FiniteDomainTest {

	@Test
	public void shouldAssignDomain() {
		Unifiable<Long> i = lvar();
		List<Long> result =
				solve(i, dom(i, EnumeratedDomain.range(0L, 10L)))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldIntersectDomains() {
		Unifiable<Long> i = lvar();

		List<Long> result =
				solve(i, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(dom(i, EnumeratedDomain.range(5L, 15L))))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainNormal() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		List<Long> result =
				solve(j, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(Constraints.unify(i, j)))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainInverted() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		List<Long> result =
				solve(j, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(Constraints.unify(j, i)))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainInvertedTransitive() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		List<Long> result =
				solve(k, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(k.unifies(j))
						.and(Constraints.unify(k, i)))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainInvertedTransitiveAndIntersect() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		List<Long> result =
				solve(k, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(k.unifies(j))
						.and(Constraints.unify(k, i))
						.and(dom(k, EnumeratedDomain.range(5L, 20L))))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldCombineTwoDomains() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		List<Tuple2<Long, Long>> results =
				solve(lval(Tuple.of(i, j)),
						dom(i, EnumeratedDomain.range(0L, 3L))
								.and(dom(j, EnumeratedDomain.range(0L, 3L))))
						.map(Term::get)
						.map(t -> t.map1(Term::get).map2(Term::get))
						.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactlyInAnyOrder(
						Tuple.of(0L, 0L),
						Tuple.of(0L, 1L),
						Tuple.of(0L, 2L),
						Tuple.of(1L, 0L),
						Tuple.of(1L, 1L),
						Tuple.of(1L, 2L),
						Tuple.of(2L, 0L),
						Tuple.of(2L, 1L),
						Tuple.of(2L, 2L)
				);
	}

	public static <T> Goal sizo(Unifiable<Long> size, Unifiable<Long> i, Unifiable<LList<T>> lst) {
		return matche(lst,
				llist(() -> size.unifies(i)),
				llist((a, d) ->
						Logic.<Long> exist(i1 ->
								FiniteDomain.addo(i, lval(1L), i1)
										.and(defer(() -> sizo(size, i1, d))))));
	}

	public static <T> Goal sizo(Unifiable<Long> size, Unifiable<LList<T>> lst) {
		return sizo(size, lval(0L), lst);
	}

	@Test
	public void shouldDiffIntervalWithNumber() {
		Unifiable<Long> i = lvar();
		Goal goal = dom(i, Interval.of(0L, 10L))
				.and(FiniteDomain.separate(i, lval(5L)));

		var result = collect(goal.solve(i)
				.map(Term::get));

		Assertions.assertThat(result)
				.allMatch(t -> t != 5L);
	}

	public static <A> Goal distinctoFd(Unifiable<LList<A>> distinct) {
		return matche(distinct,
				llist(() -> Goal.success()),
				llist(a -> Goal.success()),
				llist((a, b, d) ->
						FiniteDomain.separate(a, b)
								.and(defer(() -> distinctoFd(LList.of(a, d))))
								.and(defer(() -> distinctoFd(LList.of(b, d))))));
	}

	public static <A> Goal distinctoFd(List<Unifiable<A>> distinct) {
		return IntStream.range(0, distinct.size() - 1)
				.mapToObj(i ->
						IntStream.range(i + 1, distinct.size())
								.boxed()
								.collect(Collectors.toList()))
				.map(indices -> indices.stream()
						.map(j -> separate(distinct.get(indices.get(0) - 1), distinct.get(j)))
						.reduce(Goal::and)
						.orElseGet(Goal::success))
				.reduce(Goal::and)
				.orElseGet(Goal::success);
	}

	@Test
	public void shouldAssureDistinctTransitive() {
		Unifiable<Integer> v0 = lvar();
		Unifiable<Integer> v1 = lvar();
		Unifiable<Integer> v2 = lvar();
		Unifiable<Integer> v3 = lvar();
		Unifiable<Integer> v4 = lvar();

		int n = 5;

		Unifiable<LList<Integer>> lst = LList.ofAll(v0, v1, v2, v3, v4);
		var result = collect(distinctoFd(lst)
				.and(dom(v0, Interval.of(0, n)))
				.and(dom(v1, Interval.of(0, n)))
				.and(dom(v2, Interval.of(0, n)))
				.and(dom(v3, Interval.of(0, n)))
				.and(dom(v4, Interval.of(0, n)))
				.solve(lst)
				.map(Term::get)
				.map(LList::toValueStream)
				.map(s -> s.collect(Collectors.toList())));

		HashSet<List<Integer>> unique = new HashSet<>(result);
		Assertions.assertThat(result)
				.hasSameElementsAs(unique)
				.allMatch(l ->
						!l.get(0).equals(l.get(1)) &&
								!l.get(0).equals(l.get(2)) &&
								!l.get(0).equals(l.get(3)) &&
								!l.get(0).equals(l.get(4)) &&
								!l.get(1).equals(l.get(2)) &&
								!l.get(1).equals(l.get(3)) &&
								!l.get(1).equals(l.get(4)) &&
								!l.get(2).equals(l.get(3)) &&
								!l.get(2).equals(l.get(4)) &&
								!l.get(3).equals(l.get(4))
				);
	}

	static <T> Stream<Reified<T>> solve(Unifiable<T> out, Goal g) {
		return g.solve(out);
	}

	@Test
	public void shouldMixMultipleConstraintSystems() {
		Unifiable<String> str = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		lombok.val result = collect(
				addo(a, b, c)
						.and(dom(a, Interval.of(0, 5)))
						.and(dom(b, Interval.of(0, 5)))
						.and(dom(c, Interval.of(-5, 10)))
						.and(Disequality.separate(str, lval("123")))
						.solve(lval(Tuple.of(a, b, c, str)))
						.map(Term::get)
						.map(t -> t.map(Term::get, Term::get, Term::get, Function.identity())));

		// complete: every a+b=c combination over the domains (6×6 = 36), none lost
		// to the untouched disequality on str
		org.assertj.core.api.Assertions.assertThat(result).hasSize(36);
		org.assertj.core.api.Assertions.assertThat(result)
				.allMatch(t -> t._1 + t._2 == t._3)
				.allMatch(t -> t._1 >= 0 && t._1 <= 5 && t._2 >= 0 && t._2 <= 5);
		org.assertj.core.api.Assertions.assertThat(result.stream()
						.map(t -> Tuple.of(t._1, t._2, t._3))
						.distinct()
						.count())
				.isEqualTo(36L);
	}
}
