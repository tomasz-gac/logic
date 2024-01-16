package com.tgac.logic.finitedomain;
import com.tgac.functional.Streams;
import com.tgac.logic.Goal;
import com.tgac.logic.Logic;
import com.tgac.logic.Matche;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.step.Step;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tgac.logic.Goal.defer;
import static com.tgac.logic.Goal.success;
import static com.tgac.logic.Matche.llist;
import static com.tgac.logic.Matche.matche;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.leq;
import static com.tgac.logic.finitedomain.FiniteDomain.lss;
import static com.tgac.logic.finitedomain.FiniteDomain.separate;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

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

	@Test
	public void shouldConstrainAsLeq() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				solve(lval(Tuple.of(i, j)),
						Goal.success()
								.and(printPackage((FiniteDomain.leq(i, j))))
								.and(printPackage(dom(i, EnumeratedDomain.range(0L, 4L))))
								.and(printPackage(dom(j, EnumeratedDomain.range(0L, 4L)))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLeq2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				solve(lval(Tuple.of(i, j)),
						Goal.success()
								.and(printPackage(dom(i, EnumeratedDomain.range(0L, 4L))))
								.and(printPackage(dom(j, EnumeratedDomain.range(0L, 4L))))
								.and(printPackage((FiniteDomain.leq(i, j)))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLeqThanNumber() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();

		List<Tuple2<Long, Long>> results = solve(lval(Tuple.of(y, z)),
				Goal.success()
						.and(printPackage(dom(x, EnumeratedDomain.range(3L, 6L))))
						.and(printPackage(dom(z, EnumeratedDomain.range(3L, 6L))))
						.and(printPackage(dom(y, EnumeratedDomain.range(1L, 5L))))
						.and(printPackage(FiniteDomain.leq(x, lval(5L))))
						.and(printPackage(CKanren.unify(x, y)))
		)
				.map(Unifiable::get)
				.map(t -> t.map(Unifiable::get, Unifiable::get))
				.collect(Collectors.toList());

		System.out.println(results);

		Assertions.assertThat(results)
				.allMatch(t -> t._1 <= 5 && t._2 <= 5);
	}

	static Goal extractPackage(Goal g, Consumer<Package> c) {
		return s -> {
			Step<Package> r = g.apply(s);
			r.stream().forEach(c);
			return r;
		};
	}

	static Goal printPackage(Goal g) {
		return extractPackage(g, System.out::println)
				.named(g.toString());
	}

	@Test
	public void shouldSum() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		Instant start = Instant.now();

		Goal.Conjunction goal =
				FiniteDomain.sum(i, j, k)
						.and(FiniteDomain.separate(i, j))
						.and(dom(i, Interval.of(0L, 100L)))
						.and(dom(j, Interval.of(0L, 100L)))
						.and(dom(k, Interval.of(0L, 100L)));

		System.out.println(goal);

		java.util.List<Tuple3<Long, Long, Long>> result =
				solve(lval(Tuple.of(i, j, k)),
						goal)
						.map(Unifiable::get)
						.map(t -> t
								.map1(Unifiable::get)
								.map2(Unifiable::get)
								.map3(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(Duration.between(start, Instant.now()).toMillis() / 1000.);
		System.out.println(result.size());

		Assertions.assertThat(result.stream().allMatch(t -> t._1 + t._2 == t._3))
				.isTrue();
		Assertions.assertThat(result.stream().noneMatch(t -> t._1.equals(t._2)))
				.isTrue();
	}

	@Test
	public void shouldSum2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		java.util.List<Tuple3<Long, Long, Long>> result =
				solve(lval(Tuple.of(i, j, k)),
						CKanren.unify(i, lval(3L))
								.and(dom(k, EnumeratedDomain.range(0L, 100L)))
								.and(CKanren.unify(j, lval(2L)))
								.and(FiniteDomain.sum(i, j, k)))
						.map(Unifiable::get)
						.map(t -> t
								.map1(Unifiable::get)
								.map2(Unifiable::get)
								.map3(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactly(Tuple.of(3L, 2L, 5L));
	}

	public static <T> Goal sizo(Unifiable<Long> size, Unifiable<Long> i, Unifiable<LList<T>> lst) {
		return matche(lst,
				llist(() -> size.unify(i)),
				llist((a, d) ->
						Logic.<Long> exist(i1 ->
								FiniteDomain.sum(i, lval(1L), i1)
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

		var result = goal.solve(i)
				.map(Unifiable::get)
				.collect(Collectors.toList());

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
						IntStream.range(i + 1, distinct.size() - 1)
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
		var result = distinctoFd(lst)
				.and(dom(v0, Interval.of(0, n)))
				.and(dom(v1, Interval.of(0, n)))
				.and(dom(v2, Interval.of(0, n)))
				.and(dom(v3, Interval.of(0, n)))
				.and(dom(v4, Interval.of(0, n)))
				.solve(lst)
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(s -> s.collect(Collectors.toList()))
				.collect(Collectors.toList());

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
								!l.get(3).equals(l.get(4)));
	}

	public static Goal allLesso(Unifiable<LList<Integer>> lst) {
		return Matche.matche(lst,
				llist(() -> success()),
				llist((a) -> success()),
				llist((a, b, d) ->
						lss(a, b)
								.and(defer(() -> allLesso(LList.of(b, d))))));
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
		var result = allLesso(lst)
				.and(dom(v0, Interval.of(0, n)))
				.and(dom(v1, Interval.of(0, n)))
				.and(dom(v2, Interval.of(0, n)))
				.and(dom(v3, Interval.of(0, n)))
				.and(dom(v4, Interval.of(0, n)))
				.and(dom(v5, Interval.of(0, n)))
				.solve(lst)
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(s -> s.collect(Collectors.toList()))
				.collect(Collectors.toList());

		System.out.println(result);
		HashSet<List<Integer>> unique = new HashSet<>(result);
		Assertions.assertThat(result)
				.hasSameElementsAs(unique)
				.allMatch(l ->
						Streams.zip(l.stream(), l.stream().skip(1), Tuple::of)
								.allMatch(lr -> lr.apply((lv, rv) -> lv < rv)));
	}

	public static Goal addDigitso(
			Unifiable<Integer> augend,
			Unifiable<Integer> addend,
			Unifiable<Integer> carryIn,
			Unifiable<Integer> carryOut,
			Unifiable<Integer> digit) {
		Unifiable<Integer> partialSum = lvar();
		Unifiable<Integer> sum = lvar();
		return dom(partialSum, Interval.of(0, 18))
				.and(dom(sum, Interval.of(0, 19)))
				.and(FiniteDomain.sum(augend, addend, partialSum))
				.and(FiniteDomain.sum(partialSum, carryIn, sum))
				.and(Goal.failure()
						.or(lss(lval(9), sum)
								.and(carryOut.unify(1))
								.and(FiniteDomain.sum(digit, lval(10), sum)))
						.or(leq(sum, lval(9))
								.and(carryOut.unify(0))
								.and(digit.unify(sum))));
	}

	public static Goal sendMoreMoneyo(Unifiable<LList<Integer>> letters) {
		Unifiable<Integer> s = lvar();
		Unifiable<Integer> e = lvar();
		Unifiable<Integer> n = lvar();
		Unifiable<Integer> d = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> o = lvar();
		Unifiable<Integer> r = lvar();
		Unifiable<Integer> y = lvar();

		Unifiable<Integer> carry0 = lvar();
		Unifiable<Integer> carry1 = lvar();
		Unifiable<Integer> carry2 = lvar();
		return letters.unify(LList.ofAll(s, e, n, d, m, o, r, y))
				.and(distinctoFd(letters))
				.and(dom(s, Interval.of(1, 9)))
				.and(dom(m, Interval.of(1, 9)))
				.and(dom(e, Interval.of(0, 9)))
				.and(dom(n, Interval.of(0, 9)))
				.and(dom(d, Interval.of(0, 9)))
				.and(dom(o, Interval.of(0, 9)))
				.and(dom(r, Interval.of(0, 9)))
				.and(dom(y, Interval.of(0, 9)))
				.and(dom(carry0, Interval.of(0, 1)))
				.and(dom(carry1, Interval.of(0, 1)))
				.and(dom(carry2, Interval.of(0, 1)))
				.and(addDigitso(s, m, carry2, m, o))
				.and(addDigitso(e, o, carry1, carry2, n))
				.and(addDigitso(n, r, carry0, carry1, e))
				.and(addDigitso(d, e, lval(0), carry0, y));
	}

	@Test
	public void shouldSend() {
		Unifiable<LList<Integer>> letters = lvar();

		List<List<Integer>> result = sendMoreMoneyo(letters)
				.solve(letters)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());

		System.out.println(result);

		int S = result.get(0).get(0);
		int E = result.get(0).get(1);
		int N = result.get(0).get(2);
		int D = result.get(0).get(3);
		int M = result.get(0).get(4);
		int O = result.get(0).get(5);
		int R = result.get(0).get(6);
		int Y = result.get(0).get(7);

		Assertions.assertThat(
						asNumber(Arrays.asList(S, E, N, D))
								+ asNumber(Arrays.asList(M, O, R, E)))
				.isEqualTo(asNumber(Arrays.asList(M, O, N, E, Y)));
	}

	static <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out, Goal g) {
		return g.apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out))
				.stream();
	}

	private static int asNumber(List<Integer> digits) {
		return IntStream.range(0, digits.size())
				.mapToObj(i -> Tuple.of(i,
						BigInteger.TEN
								.pow(digits.size() - i - 1)
								.intValueExact()))
				.map(t -> t.apply((i, pow) ->
						pow * digits.get(i)))
				.reduce(Integer::sum)
				.orElse(0);
	}
}
