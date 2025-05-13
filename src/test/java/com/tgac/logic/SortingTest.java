package com.tgac.logic;

import static com.tgac.logic.LogicTest.runStream;
import static com.tgac.logic.goals.Logic.project;
import static com.tgac.logic.goals.Matche.matche;
import static com.tgac.logic.unification.LVal.lval;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.ckanren.CKanren;
import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.goals.Matche;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.ExtensionMethod;
import org.assertj.core.api.Assertions;
import org.junit.Test;

@SuppressWarnings({"unchecked", "ArraysAsListWithZeroOrOneArgument", "unused"})
@ExtensionMethod(CKanren.class)
public class SortingTest {

	public static Goal firsto(Goal... goals) {
		return Goal.condu(goals)
				.named("firsto(" + Arrays.stream(goals)
						.map(Objects::toString)
						.collect(Collectors.joining(", ")) + ")");
	}

	static <T> Goal halfo(
			Unifiable<LList<T>> lst,
			Unifiable<LList<T>> lhs,
			Unifiable<LList<T>> rhs) {
		return Logic.<LList<T>, T> exist((rest, m) ->
				Logic.appendo(lhs, rhs, lst)
						.and(Logic.sameLengtho(lhs, rhs)
								.or(Logic.sameLengtho(lhs, LList.of(m, rhs)))));
	}

	@Test
	public void shouldSplitListInHalf() {
		Unifiable<Tuple2<
				Unifiable<LList<Integer>>,
				Unifiable<LList<Integer>>>> res = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(res,
						Logic.<LList<Integer>> exist(lst ->
								lst.unify(LList.ofAll(1, 2, 3, 4, 5))
										.and(Matche.matche(res,
												Matche.tuple((l, r) -> halfo(lst, l, r)
														.and(res.unify(lval(Tuple.of(l, r)))))))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactlyInAnyOrder(Tuple.of(
						Arrays.asList(1, 2, 3),
						Arrays.asList(4, 5)));
	}

	@Test
	public void shouldSplitListInHalf2() {
		Unifiable<Tuple2<
				Unifiable<LList<Integer>>,
				Unifiable<LList<Integer>>>> res = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(res,
						Logic.<LList<Integer>> exist(lst ->
								lst.unify(LList.ofAll(1, 2, 3, 4))
										.and(Matche.matche(res,
												Matche.tuple((l, r) -> halfo(lst, l, r)
														.and(res.unify(lval(Tuple.of(l, r)))))))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactlyInAnyOrder(Tuple.of(
						Arrays.asList(1, 2),
						Arrays.asList(3, 4)));
	}

	@Test
	public void shouldSplitListInHalf3() {
		Unifiable<Tuple2<
				Unifiable<LList<Integer>>,
				Unifiable<LList<Integer>>>> res = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(res,
						Logic.<LList<Integer>> exist(lst ->
								lst.unify(LList.ofAll(1))
										.and(Matche.matche(res,
												Matche.tuple((l, r) -> halfo(lst, l, r)
														.and(res.unify(lval(Tuple.of(l, r)))))))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactlyInAnyOrder(Tuple.of(
						Arrays.asList(1),
						Arrays.asList()));
	}

	static Goal asserto(boolean b) {
		return Goal.defer(() -> b ? Goal.success() : Goal.failure());
	}

	static <A> Goal sizo(Unifiable<LList<A>> lst, long n) {
		return asserto(n >= 0)
				.and(matche(lst,
						Matche.llist(() -> asserto(n == 0)),
						Matche.llist((a, d) -> Goal.defer(() -> sizo(d, n - 1)))));
	}

	static <T> Goal middle(Unifiable<LList<T>> lst, Unifiable<T> m) {
		return Logic.<LList<T>, LList<T>, LList<T>> exist((lhs, rhs, rest) ->
				rest.unify(LList.of(m, rhs))
						.and(Logic.appendo(lhs, rest, lst)
								.and(Logic.sameLengtho(lhs, rhs)
										.or(Logic.sameLengtho(lhs, rest)))));
	}

	@Test
	public void shouldGetMiddleForEven() {
		Unifiable<Integer> m = LVar.lvar();
		Unifiable<LList<Integer>> lst = LList.ofAll(1, 2, 3, 4);
		Assertions.assertThat(LogicTest.runStream(m,
								middle(lst, m))
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactly(3);
	}

	@Test
	public void shouldGetMiddleForOdd() {
		Unifiable<Integer> m = LVar.lvar();
		Unifiable<LList<Integer>> lst = LList.ofAll(1, 2, 3);
		Assertions.assertThat(LogicTest.runStream(m,
								middle(lst, m))
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactly(2);
	}

	@Test
	public void shouldNotGetMiddleForEmpty() {
		Unifiable<Integer> m = LVar.lvar();
		Unifiable<LList<Integer>> lst = LList.ofAll();
		Assertions.assertThat(LogicTest.runStream(m,
								middle(lst, m))
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.isEmpty();
	}

	static <A> Goal partition(
			Unifiable<LList<A>> lst, Unifiable<A> mid,
			Unifiable<LList<A>> less, Unifiable<LList<A>> more,
			BiFunction<Unifiable<A>, Unifiable<A>, Goal> cmpLess) {
		return matche(lst,
				Matche.llist(() -> less.unify(LList.empty()).and(more.unify(LList.empty()))),
				Matche.llist((a, rest) -> firsto(
						cmpLess.apply(a, mid)
								.and(Matche.matche(less,
										Matche.llist((l, d) -> l.unify(a)
												.and(Goal.defer(() -> partition(rest, mid, d, more, cmpLess)))))),
						Matche.matche(more,
								Matche.llist((m, d) -> m.unify(a).and(
										Goal.defer(() -> partition(rest, mid, less, d, cmpLess))))))));
	}

	@Test
	public void shouldPartitionOdd() {
		Unifiable<Tuple2<Unifiable<LList<Integer>>, Unifiable<LList<Integer>>>> lr = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(lr,
						Matche.matche(lr, Matche.tuple((l, r) ->
								partition(LList.ofAll(3, 2, 1, 5, 4), lval(3),
										l, r, SortingTest::cmpProjection))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(Arrays.asList(2, 1), Arrays.asList(3, 5, 4)));
	}

	static <T extends Comparable<T>> Goal cmpProjection(Unifiable<T> a, Unifiable<T> b) {
		return Logic.project(a, b, (av, bv) ->
				asserto(av.compareTo(bv) < 0));
	}

	@Test
	public void shouldPartitionEven() {
		Unifiable<Tuple2<Unifiable<LList<Integer>>, Unifiable<LList<Integer>>>> lr = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(lr,
						Matche.matche(lr, Matche.tuple((l, r) ->
								partition(LList.ofAll(3, 2, 1, 4), lval(2),
										l, r, SortingTest::cmpProjection))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(Arrays.asList(1), Arrays.asList(3, 2, 4)));
	}

	@Test
	public void shouldPartitionEmpty() {
		Unifiable<Tuple2<Unifiable<LList<Integer>>, Unifiable<LList<Integer>>>> lr = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(lr,
						Matche.matche(lr, Matche.tuple((l, r) ->
								partition(LList.ofAll(), lval(2),
										l, r, SortingTest::cmpProjection))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(Collections.emptyList(), Collections.emptyList()));
	}

	@Test
	public void shouldPartitionUnbalanced() {
		Unifiable<Tuple2<Unifiable<LList<Integer>>, Unifiable<LList<Integer>>>> lr = LVar.lvar();
		List<Tuple2<List<Integer>, List<Integer>>> result = LogicTest.runStream(lr,
						Matche.matche(lr, Matche.tuple((l, r) ->
								partition(LList.ofAll(3, 2, 1, 4), lval(-1),
										l, r, SortingTest::cmpProjection))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(Collections.emptyList(), Arrays.asList(3, 2, 1, 4)));
	}

	static <A> Goal minMax(Unifiable<LList<A>> lst, Unifiable<A> min, Unifiable<A> max, Comparator<A> cmp) {
		return matche(lst,
				Matche.llist((a) -> min.unify(a).and(max.unify(a))),
				Matche.llist((a, d) -> Logic.<A, A> exist((rmin, rmax) ->
						Goal.defer(() -> minMax(d, rmin, rmax, cmp))
								.and(Logic.project(rmin, rmax, a, (rmiv, rmav, av) ->
										Goal.success().and(asserto(cmp.compare(rmiv, av) < 0)
												.and(min.unify(rmin))
												.or(asserto(cmp.compare(rmiv, av) >= 0)
														.and(min.unify(a)))
												.and(asserto(cmp.compare(rmav, av) > 0)
														.and(max.unify(rmav))
														.or(asserto(cmp.compare(rmav, av) <= 0)
																.and(max.unify(a))))))))));
	}

	@Test
	public void shouldFindMinMax() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> miMa = LVar.lvar();
		List<Tuple2<Integer, Integer>> result = LogicTest.runStream(miMa, Matche.matche(miMa, Matche.tuple((min, max) ->
						minMax(LList.ofAll(1, 2, 3, 4, 5), min, max, Integer::compareTo))))
				.map(Unifiable::get)
				.map(t -> t.map(MiniKanren.applyOnBoth(Unifiable::get)))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(1, 5));
	}

	@Test
	public void shouldFindMinMaxUnsorted() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> miMa = LVar.lvar();
		List<Tuple2<Integer, Integer>> result = LogicTest.runStream(miMa, Matche.matche(miMa, Matche.tuple((min, max) ->
						minMax(LList.ofAll(5, 3, 1, 2, 4), min, max, Integer::compareTo))))
				.map(Unifiable::get)
				.map(t -> t.map(MiniKanren.applyOnBoth(Unifiable::get)))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(1, 5));
	}

	@Test
	public void shouldFindMinMaxForSingleValued() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> miMa = LVar.lvar();
		List<Tuple2<Integer, Integer>> result = LogicTest.runStream(miMa, Matche.matche(miMa, Matche.tuple((min, max) ->
						minMax(LList.ofAll(1), min, max, Integer::compareTo))))
				.map(Unifiable::get)
				.map(t -> t.map(MiniKanren.applyOnBoth(Unifiable::get)))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Tuple.of(1, 1));
	}

	@Test
	public void shouldNotFindMinMaxForEmpty() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> miMa = LVar.lvar();
		List<Tuple2<Integer, Integer>> result = LogicTest.runStream(miMa, Matche.matche(miMa, Matche.tuple((min, max) ->
						minMax(LList.empty(), min, max, Integer::compareTo))))
				.map(Unifiable::get)
				.map(t -> t.map(MiniKanren.applyOnBoth(Unifiable::get)))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.isEmpty();
	}

	static <A> Goal pivot(Unifiable<LList<A>> lst, Unifiable<LList<A>> less, Unifiable<LList<A>> more,
			Comparator<A> cmp,
			BinaryOperator<A> mean) {
		return Logic.<A, A> exist((min, max) ->
				minMax(lst, min, max, cmp)
						.and(Logic.project(min, max, (miv, mav) ->
								partition(lst, mean.andThen(LVal::lval).apply(miv, mav), less, more,
										(a, b) -> compare(cmp, a, b)))));
	}

	private static <A> Goal compare(Comparator<A> cmp, Unifiable<A> a, Unifiable<A> b) {
		return Logic.project(a, b, (av, bv) -> asserto(cmp.compare(av, bv) < 0));
	}

	static <A> Goal pivot(Unifiable<LList<A>> lst, Unifiable<LList<A>> less, Unifiable<LList<A>> more, Comparator<A> cmp) {
		return Logic.<A> exist((m) ->
				middle(lst, m)
						.and(partition(lst, m, less, more, (a, b) -> compare(cmp, a, b))));
	}

	@Test
	public void shouldPivot() {
		Unifiable<Tuple2<Unifiable<LList<Integer>>, Unifiable<LList<Integer>>>> lr = LVar.lvar();

		System.out.println(LogicTest.runStream(lr,
						Matche.matche(lr, Matche.tuple((l, r) ->
								pivot(LList.ofAll(3, 2, 1, 2, 5), l, r,
										Integer::compareTo,
										(a, b) -> (a + b) / 2))))
				.map(SortingTest::unwrapListTuple)
				.collect(Collectors.toList()));
	}

	static <A> Goal sorted(Unifiable<LList<A>> lst, Comparator<A> cmp) {
		return lst.unify(LList.empty())
				.or(lst.unify(LList.of(LVar.lvar())))
				.or(Logic.<LList<A>, LList<A>> exist((lhs, rhs) ->
						halfo(lst, lhs, rhs)
								.and(Matche.matche(lhs, Matche.llist((l, dl) ->
										Matche.matche(rhs, Matche.llist((r, rd) -> Logic.project(l, r, (av, rv) ->
														asserto(cmp.compare(av, rv) <= 0))
												.and(Goal.defer(() -> sorted(lhs, cmp)))
												.and(Goal.defer(() -> sorted(rhs, cmp))))))))));
	}

	static <A> Goal filter(Unifiable<LList<A>> with, Unifiable<LList<A>> without, Function<Unifiable<A>, Goal> pred) {
		return matche(with,
				Matche.llist(() -> without.unify(LList.empty())),
				Matche.llist((a, d) -> Goal.condu(
						Goal.defer(() -> pred.apply(a)
								.and(Matche.matche(without,
										Matche.llist((b, e) -> b.unifyNc(a)
												.and(Goal.defer(() -> filter(d, e, pred))))))),
						Goal.defer(() -> filter(d, without, pred)))));
	}

	@Test
	public void shouldFilter() {
		Unifiable<LList<Integer>> filtered = LVar.lvar();
		List<List<Integer>> result = runStream(filtered,
				filter(LList.ofAll(1, 2, 1, 3, 1, 4),
						filtered,
						u -> Logic.project(u, v -> asserto(v != 1))))
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(l -> l.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Arrays.asList(2, 3, 4));
	}

	@Test
	public void shouldFilterWhenNoElement() {
		Unifiable<LList<Integer>> filtered = LVar.lvar();
		List<List<Integer>> result = runStream(filtered,
				filter(LList.ofAll(1, 2, 1, 3, 1, 4),
						filtered,
						u -> Logic.project(u, v -> asserto(v != 5))))
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(l -> l.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Arrays.asList(1, 2, 1, 3, 1, 4));
	}

	@Test
	public void shouldFilterWhenEmpty() {
		Unifiable<LList<Integer>> filtered = LVar.lvar();
		List<List<Integer>> result = runStream(filtered,
				filter(LList.ofAll(),
						filtered,
						u -> Logic.project(u, v -> asserto(v != 5))))
				.map(Unifiable::get)
				.map(LList::toValueStream)
				.map(l -> l.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Collections.emptyList());
	}

	static <A> Goal sorto(Unifiable<LList<A>> unsorted, Unifiable<LList<A>> sorted, Comparator<A> cmp) {
		return project(unsorted, l ->
				l.stream().allMatch(Either::isRight) ?
						sorted.unify(l.toValueStream().sorted(cmp).map(LVal::lval).collect(LList.collector())) :
						Goal.failure());
	}

	static <A> Goal qsorto(Unifiable<LList<A>> lst, Unifiable<LList<A>> sorted, BiFunction<Unifiable<A>, Unifiable<A>, Goal> cmp) {
		return Matche.matche(lst,
				Matche.llist(() -> sorted.unify(lst)),
				Matche.llist(a -> sorted.unify(lst)),
				Matche.llist((a, b, d) -> Logic.<LList<A>, LList<A>, LList<A>, LList<A>, LList<A>>
						exist((l, lhs, rhs, lsort, rsort) ->
						l.unify(LList.of(b, d)).and(
								partition(l, a, lhs, rhs, cmp)
										.and(Goal.defer(() -> qsorto(lhs, lsort, cmp))
												.and(Goal.defer(() -> qsorto(rhs, rsort, cmp)))
												.and(Logic.appendo(lsort, LList.of(a, rsort), sorted)))))));
	}

	@Test
	public void shouldSortUnsorted() {
		Unifiable<LList<Integer>> r = LVar.lvar();
		List<List<Integer>> result = runStream(r,
				qsorto(LList.ofAll(3, 2, 1, 2, 5), r, SortingTest::cmpProjection))
				.map(Unifiable::get)
				.map(l -> l.toValueStream()
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Arrays.asList(1, 2, 2, 3, 5));
	}

	@Test
	public void shouldSortWhenSorted() {
		Unifiable<LList<Integer>> r = LVar.lvar();
		List<List<Integer>> result = runStream(r,
				qsorto(LList.ofAll(1, 2, 3, 4, 5), r, SortingTest::cmpProjection))
				.map(Unifiable::get)
				.map(l -> l.toValueStream()
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Arrays.asList(1, 2, 3, 4, 5));
	}

	@Test
	public void shouldSortWhenEmpty() {
		Unifiable<LList<Integer>> r = LVar.lvar();
		List<List<Integer>> result = runStream(r,
				qsorto(LList.ofAll(), r, SortingTest::cmpProjection))
				.map(Unifiable::get)
				.map(l -> l.toValueStream()
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Collections.emptyList());
	}

	@Test
	public void shouldSortTheSame() {
		Unifiable<LList<Integer>> r = LVar.lvar();
		List<List<Integer>> result = runStream(r,
				qsorto(LList.ofAll(1, 1, 1, 1), r, SortingTest::cmpProjection))
				.map(Unifiable::get)
				.map(l -> l.toValueStream()
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result)
				.containsExactly(Arrays.asList(1, 1, 1, 1));
	}

	private static Tuple2<List<Integer>, List<Integer>> unwrapListTuple(
			Unifiable<Tuple2<Unifiable<LList<Integer>>, Unifiable<LList<Integer>>>> t) {
		return t.get()
				.map1(l -> l.get().toValueStream().collect(Collectors.toList()))
				.map2(l -> l.get().toValueStream().collect(Collectors.toList()));
	}
}
