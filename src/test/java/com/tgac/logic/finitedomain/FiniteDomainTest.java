package com.tgac.logic.finitedomain;
import com.tgac.logic.Goal;
import com.tgac.logic.Logic;
import com.tgac.logic.Matche;
import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.finitedomain.domains.EnumeratedDomain;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class FiniteDomainTest {

	@Test
	public void shouldAssignDomain() {
		Unifiable<Long> i = lvar();
		java.util.List<Long> result =
				solve(i, FiniteDomain.dom(i, EnumeratedDomain.range(0L, 10L)))
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
				solve(i, FiniteDomain.dom(i, EnumeratedDomain.range(0L, 10L))
						.and(FiniteDomain.dom(i, EnumeratedDomain.range(5L, 15L))))
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
				solve(j, FiniteDomain.dom(i, EnumeratedDomain.range(0L, 10L))
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
				solve(j, FiniteDomain.dom(i, EnumeratedDomain.range(0L, 10L))
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
				solve(k, FiniteDomain.dom(i, EnumeratedDomain.range(0L, 10L))
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
				solve(k, FiniteDomain.dom(i, EnumeratedDomain.range(0L, 10L))
						.and(k.unify(j))
						.and(CKanren.unify(k, i))
						.and(FiniteDomain.dom(k, EnumeratedDomain.range(5L, 20L))))
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
						FiniteDomain.dom(i, EnumeratedDomain.range(0L, 3L))
								.and(FiniteDomain.dom(j, EnumeratedDomain.range(0L, 3L))))
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
	public void shouldConstrainAsLess() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				solve(lval(Tuple.of(i, j)),
						Goal.success()
								.and(printPackage((FiniteDomain.leq(i, j))))
								.and(printPackage(FiniteDomain.dom(i, EnumeratedDomain.range(0L, 4L))))
								.and(printPackage(FiniteDomain.dom(j, EnumeratedDomain.range(0L, 4L)))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLess2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				solve(lval(Tuple.of(i, j)),
						Goal.success()
								.and(printPackage(FiniteDomain.dom(i, EnumeratedDomain.range(0L, 4L))))
								.and(printPackage(FiniteDomain.dom(j, EnumeratedDomain.range(0L, 4L))))
								.and(printPackage((FiniteDomain.leq(i, j)))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

		Assertions.assertThat(result)
				.allMatch(t -> t._1 <= t._2);
	}

	@Test
	public void shouldConstrainAsLessThanNumber() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();

		List<Tuple2<Long, Long>> results = solve(lval(Tuple.of(y, z)),
				Goal.success()
						.and(printPackage(FiniteDomain.dom(x, EnumeratedDomain.range(3L, 6L))))
						.and(printPackage(FiniteDomain.dom(z, EnumeratedDomain.range(3L, 6L))))
						.and(printPackage(FiniteDomain.dom(y, EnumeratedDomain.range(1L, 5L))))
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
		return s -> g.apply(s).peek(c);
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
						.and(FiniteDomain.dom(i, Interval.of(0L, 100L)))
						.and(FiniteDomain.dom(j, Interval.of(0L, 100L)))
						.and(FiniteDomain.dom(k, Interval.of(0L, 100L)));

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
								.and(FiniteDomain.dom(k, EnumeratedDomain.range(0L, 100L)))
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
		return Matche.matche(lst,
				Matche.llist(() -> size.unify(i)),
				Matche.llist((a, d) ->
						Logic.<Long> exist(i1 ->
								FiniteDomain.sum(i, lval(1L), i1)
										.and(Goal.defer(() -> sizo(size, i1, d))))));
	}

	public static <T> Goal sizo(Unifiable<Long> size, Unifiable<LList<T>> lst) {
		return sizo(size, lval(0L), lst);
	}

	@Test
	public void shouldDiffIntervalWithNumber() {
		Unifiable<Long> i = lvar();
		Goal goal = FiniteDomain.dom(i, Interval.of(0L, 10L))
				.and(FiniteDomain.separate(i, lval(5L)));

		System.out.println(goal);

		var result = goal.solve(i)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		Assertions.assertThat(result)
				.allMatch(t -> t != 5L);
	}

	@Test
	@Ignore
	public void shouldCountSize() {
		Unifiable<Long> c = lvar();
		System.out.println(solve(c,
				FiniteDomain.dom(c, EnumeratedDomain.range(0L, 100L))
						.and(sizo(c, LList.ofAll(1, 2, 3, 4))))
				.collect(Collectors.toList()));
	}

	//	Goal mul(Unifiable<Long> x, Unifiable<Long> y, Unifiable<Long> z) {
	//		return x.unify(lval(0L)).and(z.unify(lval(0L)))
	//				.or(y.unify(lval(0L)).and(z.unify(lval(0L))))
	//				.or(Goals.<Long, Long> exist((x1, z1) ->
	//						x1.separate(lval(1L))
	//								.and(sum(x1, lval(1L), x))
	//								.and(sum(y, y, z1))
	//								.and(Goal.defer())))
	//	}

	static <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out, Goal g) {
		return g.apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out))
				.toJavaStream();
	}
}
