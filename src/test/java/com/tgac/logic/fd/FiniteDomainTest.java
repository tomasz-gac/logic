package com.tgac.logic.fd;
import com.tgac.functional.Streams;
import com.tgac.logic.Goal;
import com.tgac.logic.Package;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.cKanren.PackageAccessor;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.Array;
import io.vavr.collection.HashSet;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;
import static com.tgac.logic.fd.FDSupport.dom;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class FiniteDomainTest {

	static {
		FDSupport.useFD();
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
								.and(printPackage(dom(i, EnumeratedInterval.of(HashSet.range(0L, 4L)))))
								.and(printPackage(dom(j, EnumeratedInterval.of(HashSet.range(0L, 4L)))))
								.and(printPackage((FDSupport.leq(i, j)))))
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
						.and(printPackage(FDSupport.leq(x, lval(5L))))
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
		return extractPackage(g, System.out::println);
	}

	@Test
	public void shouldSum() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		java.util.List<Tuple3<Long, Long, Long>> result =
				solve(lval(Tuple.of(i, j, k)),
						dom(i, EnumeratedInterval.of(HashSet.range(0L, 10L)))
								.and(dom(j, EnumeratedInterval.of(HashSet.range(0L, 10L))))
								.and(dom(k, EnumeratedInterval.of(HashSet.range(0L, 10L))))
								.and(plus(i, j, k)))
						.map(Unifiable::get)
						.map(t -> t
								.map1(Unifiable::get)
								.map2(Unifiable::get)
								.map3(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(
				Streams.zip(
								result.stream().map(Object::toString),
								IntStream.range(0, result.size()).boxed(),
								(t, x) -> x + ": " + t
						)
						.collect(Collectors.joining("\n")));
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
								.and(plus(i, j, k)))
						.map(Unifiable::get)
						.map(t -> t
								.map1(Unifiable::get)
								.map2(Unifiable::get)
								.map3(Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(result);

	}

	public static <T extends Number> Goal plus(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return CKanren.constructGoal(plusFD(a, b, rhs));
	}

	static <T extends Number> PackageAccessor plusFD(Unifiable<T> a, Unifiable<T> b, Unifiable<T> rhs) {
		return FDSupport.constraintOperation(
				p -> plusFD(a, b, rhs).apply(p),
				Array.of(a, b, rhs), (vds, p) ->
						Tuple.of(vds.get(0), vds.get(1), vds.get(2))
								.apply((u, v, w) -> Tuple.of(
												u.getDomain().min(), v.getDomain().min(), w.getDomain().min(),
												u.getDomain().max(), v.getDomain().max(), w.getDomain().max())
										.apply((uMin, vMin, wMin, uMax, vMax, wMax) ->
												EnumeratedInterval.of(HashSet.range(
																uMin + vMin,
																uMax + vMax + 1))
														.processDom(w.getUnifiable())
														.compose(EnumeratedInterval.of(HashSet.range(
																		wMin - uMax,
																		wMax - uMin + 1))
																.processDom(v.getUnifiable()))
														.compose(EnumeratedInterval.of(HashSet.range(
																		wMin - vMax,
																		wMax - vMin + 1))
																.processDom(u.getUnifiable()))
														.apply(p))
								));
	}

	static <T> java.util.stream.Stream<Unifiable<T>> solve(Unifiable<T> out, Goal g) {
		return g.apply(Package.empty())
				.flatMap(s -> CKanren.reify(s, out))
				.toJavaStream();
	}
}
