package com.tgac.logic.fd;
import com.tgac.logic.Goal;
import com.tgac.logic.Goals;
import com.tgac.logic.LList;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.fd.domains.EnumeratedInterval;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.HashSet;
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

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;
import static com.tgac.logic.fd.FDGoals.dom;
import static com.tgac.logic.fd.FDGoals.leq;
import static com.tgac.logic.fd.FDGoals.sum;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class FiniteDomainTest {

	static {
		FDGoals.useFD();
	}

	@Test
	public void shouldAssignDomain() {
		Unifiable<Long> i = lvar();
		java.util.List<Long> result =
				solve(i, dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L))))
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
				solve(i, dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L)))
						.and(dom(i, EnumeratedInterval.of(HashSet.range(5L, 15L)))))
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
				solve(j, dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L)))
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
				solve(j, dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L)))
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
				solve(k, dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L)))
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
				solve(k, dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L)))
						.and(k.unify(j))
						.and(CKanren.unify(k, i))
						.and(dom(k, EnumeratedInterval.of(HashSet.range(5L, 20L)))))
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
						dom(i, EnumeratedInterval.of(HashSet.range(0L, 3L)))
								.and(dom(j, EnumeratedInterval.of(HashSet.range(0L, 3L)))))
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
								.and(printPackage((FDGoals.leq(i, j))))
								.and(printPackage(dom(i, EnumeratedInterval.of(HashSet.range(0L, 4L)))))
								.and(printPackage(dom(j, EnumeratedInterval.of(HashSet.range(0L, 4L))))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);
	}

	@Test
	public void shouldConstrainAsLess2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();

		java.util.List<Tuple2<Long, Long>> result =
				solve(lval(Tuple.of(i, j)),
						Goal.success()
								.and(printPackage(dom(i, EnumeratedInterval.of(HashSet.range(0L, 4L)))))
								.and(printPackage(dom(j, EnumeratedInterval.of(HashSet.range(0L, 4L)))))
								.and(printPackage((FDGoals.leq(i, j)))))
						.map(Unifiable::get)
						.map(t -> t.map1(Unifiable::get).map2(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);
	}

	@Test
	public void shouldConstrainAsLessThanNumber() {
		Unifiable<Long> x = lvar();
		Unifiable<Long> y = lvar();
		Unifiable<Long> z = lvar();

		List<Tuple2<Unifiable<Long>, Unifiable<Long>>> results = solve(lval(Tuple.of(y, z)),
				Goal.success()
						.and(printPackage(dom(x, EnumeratedInterval.of(HashSet.range(3L, 6L)))))
						.and(printPackage(dom(z, EnumeratedInterval.of(HashSet.range(3L, 6L)))))
						.and(printPackage(dom(y, EnumeratedInterval.of(HashSet.range(1L, 5L)))))
						.and(printPackage(FDGoals.leq(x, lval(5L))))
						.and(printPackage(CKanren.unify(x, y)))
		)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		System.out.println(results);
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
				sum(i, j, k)
						.and(dom(i, EnumeratedInterval.of(HashSet.range(0L, 100))))
						.and(dom(j, EnumeratedInterval.of(HashSet.range(0L, 100))))
						.and(dom(k, EnumeratedInterval.of(HashSet.range(0L, 100))));

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

		//		System.out.println(
		//				Streams.zip(
		//								result.stream().map(Object::toString),
		//								IntStream.range(0, result.size()).boxed(),
		//								(t, x) -> x + ": " + t
		//						)
		//						.collect(Collectors.joining("\n")));
	}

	@Test
	public void shouldSum2() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		java.util.List<Tuple3<Long, Long, Long>> result =
				solve(lval(Tuple.of(i, j, k)),
						CKanren.unify(i, lval(3L))
								.and(dom(k, EnumeratedInterval.of(HashSet.range(0L, 100L))))
								.and(CKanren.unify(j, lval(2L)))
								.and(sum(i, j, k)))
						.map(Unifiable::get)
						.map(t -> t
								.map1(Unifiable::get)
								.map2(Unifiable::get)
								.map3(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);
	}

	/**
	 * <pre>
	 *      S E N D
	 *    + M O R E
	 *    ----------
	 *    M O N E Y
	 * </pre>
	 */
	@Test
	@Ignore
	public void shouldSolveSendMoreMoney() {
		Unifiable<Long> S = lvar("S");
		Unifiable<Long> E = lvar("E");
		Unifiable<Long> N = lvar("N");
		Unifiable<Long> D = lvar("D");

		Unifiable<Long> M = lvar("M");
		Unifiable<Long> O = lvar("O");
		Unifiable<Long> R = lvar("R");

		Unifiable<Long> Y = lvar("Y");

		EnumeratedInterval<Long> interval0 = EnumeratedInterval.of(HashSet.range(0L, 10L));
		EnumeratedInterval<Long> interval1 = EnumeratedInterval.of(HashSet.range(1L, 10L));
		Goal.Conjunction all = Goal.all(
				printPackage(dom(S, interval1)),
				printPackage(dom(E, interval0)),
				printPackage(dom(N, interval0)),
				printPackage(dom(D, interval0)),
				printPackage(dom(M, interval1)),
				printPackage(dom(O, interval0)),
				printPackage(dom(R, interval0)),
				printPackage(dom(Y, interval0)),
				printPackage(sum(S, M, O)),
				printPackage(sum(E, O, N)),
				printPackage(sum(N, R, E)),
				printPackage(sum(D, E, Y)),
				printPackage(Goals.distincto(LList.ofAll(S, E, N, D, M, O, R, Y))));

		System.out.println(all);
		var result =
				solve(lval(Tuple.of(S, E, N, D, M, O, R, Y)),
						printPackage(all))
						.limit(1)
						.map(Unifiable::get)
						.collect(Collectors.toList());

		System.out.println(result);
	}

	public static Goal addDigitso(Unifiable<Long> augend, Unifiable<Long> addend,
			Unifiable<Long> carryIn, Unifiable<Long> carryOut, Unifiable<Long> digit) {
		return Goals.<Long, Long> exist((partialSum, sum) ->
				dom(partialSum, EnumeratedInterval.range(0L, 19L))
						.and(dom(sum, EnumeratedInterval.range(0L, 20L)))
						.and(sum(augend, addend, partialSum))
						.and(sum(partialSum, carryIn, sum))
						.and(Goal.conde(
								leq(lval(9L), sum)
										.and(sum.separate(9L))
										.and(carryOut.unify(1L))
										.and(sum(digit, lval(10L), sum)),
								leq(sum, lval(9L))
										.and(carryOut.unify(0L))
										.and(digit.unify(sum)))));
	}

	@Test
	public void shouldSolveSendMoreMoneySolution() {

	}

	static <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out, Goal g) {
		return g.apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out))
				.toJavaStream();
	}
}
