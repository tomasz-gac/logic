package org.clauseway.logic.finitedomain;

import org.clauseway.logic.TestSchedulers;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.Utils.collect;
import static org.clauseway.logic.finitedomain.FiniteDomain.dom;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.goals.Matche.llist;
import static org.clauseway.logic.goals.Matche.matche;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import org.clauseway.logic.constraints.Constraints;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.goals.Logic;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LTree;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
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
				solve(i, dom(i, Longs.range(0, 10)))
						.map(Term::get)
						.collect(Collectors.toList());

		Assertions.assertThat(result)
				.containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
	}

	@Test
	public void anLTreeLeafWithADomainEnumeratesAtReify() {
		// decompose knows trees; enforcement must too — a domain-carrying
		// leaf inside an LTree grounds at reify like any list member
		Unifiable<Long> leaf = lvar();
		Unifiable<LTree<Long>> tree = LTree.of(leaf);
		List<String> result = solve(tree, dom(leaf, Longs.range(1, 3)))
				.map(Object::toString)
				.collect(Collectors.toList());

		Assertions.assertThat(result).hasSize(2);
		Assertions.assertThat(result.toString()).contains("1").contains("2");
	}

	@Test
	public void shouldIntersectDomains() {
		Unifiable<Long> i = lvar();

		List<Long> result =
				solve(i, dom(i, Longs.range(0, 10))
						.and(dom(i, Longs.range(5, 15))))
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
				solve(j, dom(i, Longs.range(0, 10))
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
				solve(j, dom(i, Longs.range(0, 10))
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
				solve(k, dom(i, Longs.range(0, 10))
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
				solve(k, dom(i, Longs.range(0, 10))
						.and(k.unifies(j))
						.and(Constraints.unify(k, i))
						.and(dom(k, Longs.range(5, 20))))
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
						dom(i, Longs.range(0, 3))
								.and(dom(j, Longs.range(0, 3))))
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
								Longs.addo(i, lval(1L), i1)
										.and(defer(() -> sizo(size, i1, d))))));
	}

	public static <T> Goal sizo(Unifiable<Long> size, Unifiable<LList<T>> lst) {
		return sizo(size, lval(0L), lst);
	}

	@Test
	public void shouldDiffIntervalWithNumber() {
		Unifiable<Long> i = lvar();
		Goal goal = dom(i, Longs.interval(0, 10))
				.and(Longs.separate(i, lval(5L)));

		var result = collect(goal.solve(i, TestSchedulers.factory())
				.map(Term::get));

		Assertions.assertThat(result)
				.allMatch(t -> t != 5L);
	}

	public static Goal distinctoFd(Unifiable<LList<Integer>> distinct) {
		return matche(distinct,
				llist(() -> Goal.success()),
				llist(a -> Goal.success()),
				llist((a, b, d) ->
						Ints.separate(a, b)
								.and(defer(() -> distinctoFd(LList.of(a, d))))
								.and(defer(() -> distinctoFd(LList.of(b, d))))));
	}

	public static Goal distinctoFd(List<Unifiable<Integer>> distinct) {
		return IntStream.range(0, distinct.size() - 1)
				.mapToObj(i ->
						IntStream.range(i + 1, distinct.size())
								.boxed()
								.collect(Collectors.toList()))
				.map(indices -> indices.stream()
						.map(j -> (Goal) Ints.separate(distinct.get(indices.get(0) - 1), distinct.get(j)))
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
				.and(dom(v0, Ints.interval(0, n)))
				.and(dom(v1, Ints.interval(0, n)))
				.and(dom(v2, Ints.interval(0, n)))
				.and(dom(v3, Ints.interval(0, n)))
				.and(dom(v4, Ints.interval(0, n)))
				.solve(lst, TestSchedulers.factory())
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
		return g.solve(out, TestSchedulers.factory());
	}

	@Test
	public void shouldMixMultipleConstraintSystems() {
		Unifiable<String> str = lvar();
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		lombok.val result = collect(
				Ints.addo(a, b, c)
						.and(dom(a, Ints.interval(0, 5)))
						.and(dom(b, Ints.interval(0, 5)))
						.and(dom(c, Ints.interval(-5, 10)))
						.and(exclude(str.unifies(lval("123"))))
						.solve(lval(Tuple.of(a, b, c, str)), TestSchedulers.factory())
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
