package com.tgac.logic.finitedomain;

import static com.tgac.logic.Goal.defer;
import static com.tgac.logic.Matche.llist;
import static com.tgac.logic.Matche.matche;
import static com.tgac.logic.Utils.collect;
import static com.tgac.logic.finitedomain.FiniteDomain.addo;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.separate;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.category.Monad;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.Goal;
import com.tgac.logic.Logic;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.separate.Disequality;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
		java.util.List<Long> result =
				solve(i, dom(i, EnumeratedDomain.range(0L, 10L)))
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldIntersectDomains() {
		Unifiable<Long> i = lvar();

		java.util.List<Long> result =
				solve(i, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(dom(i, EnumeratedDomain.range(5L, 15L))))
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainNormal() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Long> result =
				solve(j, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(CKanren.unify(i, j)))
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainInverted() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Long> result =
				solve(j, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(CKanren.unify(j, i)))
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainInvertedTransitive() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		java.util.List<Long> result =
				solve(k, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(k.unify(j))
						.and(CKanren.unify(k, i)))
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldUnifyWithDomainInvertedTransitiveAndIntersect() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		java.util.List<Long> result =
				solve(k, dom(i, EnumeratedDomain.range(0L, 10L))
						.and(k.unify(j))
						.and(CKanren.unify(k, i))
						.and(dom(k, EnumeratedDomain.range(5L, 20L))))
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void shouldCombineTwoDomains() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> results =
				solve(lval(Tuple.of(i, j)),
						dom(i, EnumeratedDomain.range(0L, 3L))
								.and(dom(j, EnumeratedDomain.range(0L, 3L))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(results);

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
				llist(() -> size.unify(i)),
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

		System.out.println(goal);

		var result = collect(goal.solve(i)
				.map(Unifiable::get));

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
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(s -> s.collect(Collectors.toList())));

		System.out.println(result);
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

	static <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out, Goal g) {
		return collect(g.apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out)))
				.stream();
	}

	@Test
	public void shouldMixMultipleConstraintSystems() {
		Unifiable<String> str = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		System.out.println(collect(
				addo(a, b, c)
						.and(dom(a, Interval.of(0, 5)))
						.and(dom(b, Interval.of(0, 5)))
						.and(dom(c, Interval.of(-5, 10)))
						.and(Disequality.separate(str, lval("123")))
						.solve(lval(Tuple.of(a, b, c, str)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get, Function.identity()))
		));
	}
}
