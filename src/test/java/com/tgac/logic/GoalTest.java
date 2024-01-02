package com.tgac.logic;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Stream;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tgac.logic.Goal.defer;
import static com.tgac.logic.Goal.separate;
import static com.tgac.logic.Goals.appendo;
import static com.tgac.logic.Goals.firsto;
import static com.tgac.logic.Goals.llist;
import static com.tgac.logic.Goals.matche;
import static com.tgac.logic.Goals.membero;
import static com.tgac.logic.Goals.rembero;
import static com.tgac.logic.Goals.sameLengtho;
import static com.tgac.logic.LVal.lval;
import static com.tgac.logic.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
public class GoalTest {

	public <T> Goal caro(
			Unifiable<T> lhs,
			Unifiable<LList<T>> cons) {
		return cons.unify(LList.of(lhs, lvar()));
	}

	@Test
	public void test() {
		Unifiable<Integer> head = lvar();
		Unifiable<LList<Integer>> tail = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		List<Integer> collect = runStream(lst,
				lst.unify(LList.ofAll(1, 2, 3, 4)),
				tail.unify((LList.ofAll(2, 3, 4))),
				lst.unify(LList.of(head, tail)))
				.map(Unifiable::get)
				.flatMap(LList::toValueStream)
				.collect(Collectors.toList());
		assertThat(collect)
				.containsExactly(1, 2, 3, 4);
	}
	public static <T> java.util.stream.Stream<Unifiable<T>> runStream(Unifiable<T> x, Goal... goals) {
		return Goal.success().and(goals)
				.solve(x);
	}

	@Test
	public void shouldUnpack() {
		Unifiable<LList<Integer>> lst = lvar();
		Unifiable<Integer> head = lvar();

		val result = runStream(lval(Tuple.of(head, lst)),
				head.unify(3),
				caro(head, lst))
				.collect(Collectors.toList());
		assertThat(result)
				.hasSize(1);
		assertThat(result.get(0).get()._1)
				.isEqualTo(lval(3));
		assertThat(result.get(0).get()._2.get().getHead())
				.isEqualTo(lval(3));
		assertThat(result.get(0).get()._2.get().getTail())
				.matches(s -> s.asVar().isDefined());
	}

	@Test
	public void shouldAppend() {
		Unifiable<LList<Integer>> x = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		Unifiable<LList<Integer>> res = lvar();
		val out = lval(Tuple.of(lst, x, res));
		val results = runStream(out,
				appendo(lst, x, res))
				.map(Unifiable::asVal)
				.map(Option::get)
				.limit(3)
				.map(Object::toString)
				.collect(Collectors.toList());
		assertThat(results)
				.containsExactly(
						"({()}, <_.1>, <_.1>)",
						"({(<_.2>)}, <_.3>, {(<_.2> . <_.3>)})",
						"({(<_.3>, <_.4>)}, <_.5>, {(<_.3>, <_.4> . <_.5>)})");
		System.out.println(results.stream().map(Object::toString)
				.collect(Collectors.joining("\n")));
	}

	@Test
	public void shouldAppend2() {
		Unifiable<LList<Integer>> x = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		Unifiable<LList<Integer>> res = lvar();
		val result = runStream(x,
				lst.unify(LList.ofAll(1, 2, 3)),
				res.unify(LList.ofAll(1, 2, 3, 4, 5, 6)),
				appendo(lst, x, res))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result.get(0).get()
				.stream()
				.map(Either::get)
				.map(Unifiable::get)
				.collect(Collectors.toList()))
				.containsExactlyElementsOf(Arrays.asList(4, 5, 6));
	}

	@Test
	public void shouldAppend3() {
		Unifiable<LList<Integer>> x = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		Unifiable<LList<Integer>> res = lvar();
		val result = runStream(lst,
				x.unify(LList.ofAll(4, 5, 6)),
				res.unify(LList.ofAll(1, 2, 3, 4, 5, 6)),
				appendo(lst, x, res))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result.get(0).get().toValueStream()
				.collect(Collectors.toList()))
				.containsExactlyElementsOf(Arrays.asList(1, 2, 3));
	}

	@Test
	public void shouldAppend4() {
		Unifiable<LList<Integer>> x = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		Unifiable<LList<Integer>> res = lvar();
		val result = runStream(res,
				x.unify(LList.ofAll(4, 5, 6)),
				lst.unify(LList.ofAll(1, 2, 3)),
				appendo(lst, x, res))
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result.get(0).get().toValueStream()
				.collect(Collectors.toList()))
				.containsExactlyElementsOf(Arrays.asList(1, 2, 3, 4, 5, 6));
	}

	@Test
	public void shouldProduceSameLengthLists() {
		Unifiable<LList<Integer>> lhs = lvar();
		Unifiable<LList<Integer>> rhs = lvar();
		val results = runStream(
				lval(Tuple.of(lhs, rhs)),
				sameLengtho(lhs, rhs))
				.limit(10)
				.collect(Collectors.toList());
		System.out.println(results);
		results.forEach(t ->
				assertThat(t.get()._1.get().stream().count())
						.isEqualTo(t.get()._2.get().stream().count()));
	}

	public <A> Goal reversoUnsafe(Unifiable<LList<A>> lhs, Unifiable<LList<A>> rhs) {
		Unifiable<A> rHead = lvar();
		Unifiable<LList<A>> rTail = lvar();
		Unifiable<LList<A>> res = lvar();
		return rhs.unify(LList.empty()).and(lhs.unify(LList.empty()))
				.or(rhs.unify(LList.of(rHead, rTail))
						.and(appendo(res, LList.of(rHead), lhs))
						.and(defer(() -> reversoUnsafe(rTail, res))));
	}

	public <A> Goal reverso(Unifiable<LList<A>> lhs, Unifiable<LList<A>> rhs) {
		return sameLengtho(lhs, rhs).and(reversoUnsafe(lhs, rhs));
	}

	@Test
	public void shouldReverse() {
		Unifiable<LList<Integer>> reversed = lvar();
		List<Unifiable<LList<Integer>>> run = runStream(reversed,
				reverso(reversed,
						LList.ofAll(
								IntStream.range(0, 20)
										.boxed()
										.collect(Collectors.toList()))))
				.collect(Collectors.toList());

		System.out.println(run);
		assertThat(run.get(0))
				.isEqualTo(lval(Stream.range(0, 20)
						.map(i -> 20 - i - 1)
						.map(LVal::lval)
						.collect(LList.collector()))
						.get());
	}

	@Test
	public void shouldReverse2() {
		Unifiable<LList<Integer>> normal = lvar();
		Unifiable<LList<Integer>> reversed = lvar();
		List<String> result = runStream(lval(Tuple.of(normal, reversed)),
				reverso(
						normal,
						reversed))
				.limit(5)
				.map(Object::toString)
				.collect(Collectors.toList());
		System.out.println(String.join("\n", result));
		assertThat(result)
				.containsExactly(
						"{({()}, {()})}",
						"{({(<_.1>)}, {(<_.1>)})}",
						"{({(<_.3>, <_.2>)}, {(<_.2>, <_.3>)})}",
						"{({(<_.5>, <_.4>, <_.3>)}, {(<_.3>, <_.4>, <_.5>)})}",
						"{({(<_.7>, <_.6>, <_.5>, <_.4>)}, {(<_.4>, <_.5>, <_.6>, <_.7>)})}");
	}

	@Test
	public void shouldReverse3() {
		Unifiable<LList<Integer>> normal = lvar();
		System.out.println(runStream(normal,
				sameLengtho(normal, LList.ofAll(IntStream.range(0, 100)
						.boxed()
						.collect(Collectors.toList()))),
				reverso(normal, normal))
				.map(Object::toString)
				.collect(Collectors.joining("\n")));
	}

	public <A> Goal palindromo(Unifiable<LList<A>> palindrome) {
		Unifiable<A> head = lvar();
		Unifiable<LList<A>> tail = lvar();
		Unifiable<LList<A>> middle = lvar();
		return palindrome.unify(LList.empty())
				.or(palindrome.unify(LList.of(head, tail))
						.and(appendo(middle, LList.of(head), tail))
						.and(defer(() -> palindromo(middle))));
	}

	public <A> Goal palindromo2(Unifiable<LList<A>> palindrome) {
		return reverso(palindrome, palindrome);
	}

	@Test
	public void shouldWritePalindrome() {
		Unifiable<LList<Integer>> lst = lvar();
		List<Unifiable<Integer>> collected = runStream(lst,
				sameLengtho(lst, LList.ofAll(Stream.range(0, 200).collect(Collectors.toList()))),
				palindromo2(lst))
				.findFirst()
				.get()
				.get()
				.stream()
				.map(Either::get)
				.collect(Collectors.toList());
		System.out.println(collected);
		for (int i = 0; i < 200 / 2; ++i) {
			assertThat(collected.get(i))
					.isEqualTo(collected.get(199 - i));
		}
	}

	@Test
	public void shouldFindMember() {
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		List<Integer> xs = runStream(x,
				lst.unify(LList.ofAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
				membero(x, lst))
				.map(Unifiable::get)
				.collect(Collectors.toList());
		System.out.println(xs);
		assertThat(xs)
				.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
	}

	static <A> Goal lists(Unifiable<LList<A>> lists) {
		return lists.unify(LList.empty())
				.or(Goals.<A, LList<A>> exist((head, tail) ->
						lists.unify(LList.of(head, tail))
								.and(defer(() -> lists(tail)))));
	}

	@Test
	public void shouldRefreshVariablesOnDisjunction() {
		Unifiable<LList<Integer>> lst = lvar();
		System.out.println(runStream(lst, lists(lst))
				.limit(4)
				.collect(Collectors.toList()));
	}

	@Test
	public void shouldUnifyWithConstraints() {
		Unifiable<Integer> out = lvar();
		List<Integer> result = runStream(out,
				Goal.separate(out, lval(2)),
				Goal.unify(out, lval(3)))
				.map(Unifiable::get)
				.collect(Collectors.toList());
		System.out.println(result);
		assertThat(result).containsExactly(3);
	}

	@Test
	public void shouldNotUnifyWithConstraints() {
		Unifiable<Integer> out = lvar();
		assertThat(runStream(out,
				Goal.separate(out, lval(2)),
				Goal.unify(out, lval(2))))
				.isEmpty();
	}

	@Test
	public void shouldNotUnifyWhenConstraintsAlreadyViolated() {
		Unifiable<Integer> out = lvar();
		assertThat(runStream(out,
				Goal.unify(out, lval(2)),
				Goal.separate(out, lval(2))))
				.isEmpty();
	}

	@Test
	public void shouldUnifyWithSimultaneousConstraints() {
		Unifiable<Integer> out = lvar();
		assertThat(runStream(out, Goals.<Integer, Integer, Tuple2<Unifiable<Integer>, Unifiable<Integer>>> exist((x, y, p) ->
				p.unify(Tuple.of(x, out))
						.and(separate(p, lval(Tuple.of(lval(3), lval(2)))))
						.and(x.unify(3))
						.and(out.unify(3))))
				.map(Unifiable::get))
				.containsExactly(3);
	}

	@Test
	public void shouldNotUnifyWithSimultaneousConstraints() {
		Unifiable<Integer> out = lvar();
		assertThat(
				runStream(out,
						Goals.<Integer, Integer, Tuple2<Unifiable<Integer>, Unifiable<Integer>>> exist((x, y, p) ->
								p.unify(Tuple.of(x, out))
										.and(separate(p, lval(Tuple.of(lval(3), lval(2)))))
										.and(x.unify(3))
										.and(out.unify(2)))))
				.isEmpty();
	}

	public static <T> Goal brokenRembero(Unifiable<LList<T>> ls, Unifiable<T> x, Unifiable<LList<T>> out) {
		return ls.unify(LList.empty()).and(out.unify(LList.empty()))
				.or(Goals.<T, LList<T>> exist((a, d) ->
						ls.unify(LList.of(a, d))
								.and(x.unify(a))
								.and(out.unify(d))))
				.or(Goals.<T, LList<T>, LList<T>> exist((a, d, res) ->
						ls.unify(LList.of(a, d))
								.and(out.unify(LList.of(a, res)))
								.and(defer(() -> brokenRembero(d, x, res)))));
	}

	@Test
	public void shouldRemoveAllMembers() {
		Unifiable<LList<Integer>> out = lvar();
		List<List<Integer>> result = runStream(out,
				Goals.<LList<Integer>> exist(l ->
						l.unify(LList.ofAll(3, 2, 3, 2))
								.and(brokenRembero(l, lval(2), out))))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result.stream().map(Object::toString).collect(Collectors.joining("\n")));
		assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(3, 3, 2),
						Arrays.asList(3, 2, 3),
						Arrays.asList(3, 2, 3, 2));
	}

	@Test
	public void shouldRemoveSingleMember() {
		Unifiable<LList<Integer>> out = lvar();
		List<List<Integer>> result = runStream(out,
				Goals.<LList<Integer>> exist(l ->
						l.unify(LList.ofAll(3, 2, 3, 2))
								.and(rembero(l, lval(2), out))))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.collect(Collectors.toList());
		System.out.println(result.stream().map(Object::toString).collect(Collectors.joining("\n")));
		assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(3, 3, 2));
	}

	@Test
	public void shouldAddSingleMember() {
		Unifiable<LList<Integer>> out = lvar();
		List<List<Integer>> result = runStream(out,
				Goals.<LList<Integer>> exist(l ->
						l.unify(LList.ofAll(3, 2, 3, 2))
								.and(rembero(out, lval(2), l))))
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList()))
				.limit(10)
				.collect(Collectors.toList());
		System.out.println(result.stream().map(Object::toString).collect(Collectors.joining("\n")));
		assertThat(result)
				.containsExactlyInAnyOrder(
						Arrays.asList(2, 3, 2, 3, 2),
						Arrays.asList(3, 2, 2, 3, 2));
	}

	@Test
	public void shouldReturnConstraints() {
		Unifiable<LList<Tuple2<Unifiable<Integer>, Unifiable<Integer>>>> u = lvar();
		String result = runStream(u,
				Goals.<Tuple2<Unifiable<Integer>, Unifiable<Integer>>,
						LList<Tuple2<Unifiable<Integer>, Unifiable<Integer>>>,
						Integer, Integer, Integer> exist((a, d, dummy, x, y) ->
						u.unify(LList.of(a, LList.of(lval(Tuple.of(y, x)), d)))
								.and(Goal.unify(a, lval(Tuple.of(x, y))))
								.and(separate(x, lval(3)))
								.and(separate(y, lval(2)))
								.and(separate(a, lval(Tuple.of(lval(7), lval(8)))))
								.and(separate(dummy, lval(5)))))
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
		System.out.println(result);
		// {({(<_.3>, <_.2>)}, {(<_.2>, <_.3>)} . <_.4>)} : (<_.3> ≠ {3}) || (<_.2> ≠ {2}) || (<_.2> ≠ {8} && <_.3> ≠ {7})
		assertThat(result)
				.contains("{({(<_.3>, <_.2>)}, {(<_.2>, <_.3>)} . <_.4>)}")
				.contains("(<_.3> ≠ {3})")
				.contains("(<_.2> ≠ {2})")
				.contains("<_.2> ≠ {8}")
				.contains("<_.3> ≠ {7}")
				.contains("||")
				.contains("&&");
	}

	@Test
	public void shouldNotIncludeUnrelatedConstraints() {
		Unifiable<Integer> u = lvar();
		String result = runStream(u,
				Goals.<Integer, Integer, Integer> exist((x, y, z) ->
						separate(u, lval(3))
								.and(separate(x, lval(2)))
								.and(separate(y, lval(2)))
								.and(separate(z, lval(2)))))
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
		System.out.println(result);
		assertThat(result)
				.isEqualTo("<_.0> : (<_.0> ≠ {3})");
	}

	@Test
	public void shouldNotIncludeSubsumedConstraints() {
		Unifiable<Tuple2<Unifiable<Integer>, Unifiable<Integer>>> u = lvar();
		String result = runStream(u,
				Goals.<Tuple2<Unifiable<Integer>, Unifiable<Integer>>, Integer, Integer> exist((p, y, z) ->
						// p cannot be (3, 2)
						separate(p, lval(Tuple.of(lval(3), lval(2))))
								// p is y, z so y ≠ 3 && z ≠ 2
								.and(p.unify(Tuple.of(y, z)))
								.and(u.unify(p))
								// y ≠ 3 is more general than (y ≠ 3 && z ≠ 2)
								.and(separate(y, lval(3)))))
				.map(Object::toString)
				.collect(Collectors.joining("\n"));
		System.out.println(result);
		assertThat(result)
				.isEqualTo("{(<_.0>, <_.1>)} : (<_.0> ≠ {3})");
	}

	static <A> Goal removo(Unifiable<LList<A>> with, Unifiable<LList<A>> without, Unifiable<A> item) {
		return with.unify(LList.empty()).and(without.unify(LList.empty()))
				.or(Goals.<A, LList<A>> exist((a, d) ->
						with.unify(LList.of(a, d))
								.and(a.unify(item))
								.and(defer(() -> removo(d, without, item)))))
				.or(Goals.<A, LList<A>, LList<A>> exist((a, d, res) ->
						with.unify(LList.of(a, d))
								.and(separate(a, item))
								.and(without.unify(LList.of(a, res)))
								.and(defer(() -> removo(d, res, item)))));
	}

	@Test
	public void shouldRemovo() {
		Unifiable<LList<Integer>> r = lvar();
		System.out.println(runStream(r,
				Goals.<LList<Integer>> exist(l ->
						l.unify(LList.ofAll(1, 2, 1, 3))
								.and(removo(l, r, lval(1)))))
				.limit(4)
				.collect(Collectors.toList()));
	}

	static <A> Goal removeAllo(Unifiable<LList<A>> with, Unifiable<LList<A>> without, Unifiable<A> item) {
		return with.unify(LList.empty()).and(without.unify(LList.empty()))
				.or(Goals.<LList<A>> exist(res ->
						rembero(with, item, res)
								.and(with.unify(res).and(res.unify(without))
										.or(with.separate(res)
												.and(defer(() -> removeAllo(res, without, item)))))))
				.debug("recur", HashMap.of("with", with, "without", without));
	}

	@Test
	public void shouldRemoveAll() {
		Unifiable<LList<Integer>> r = lvar();
		System.out.println(runStream(r,
				Goals.<LList<Integer>> exist(l ->
						l.unify(LList.ofAll(1, 2, 1, 3, 1, 4, 1))
								.and(removeAllo(l, r, lval(1)))))
				.limit(4)
				.collect(Collectors.toList()));
	}

	static <A> Goal distincto(Unifiable<LList<A>> any, Unifiable<LList<A>> distinct) {
		return any.unify(LList.empty())
				.and(distinct.unify(LList.empty()))
				.or(matche(any, llist((head, tail) ->
						matche(distinct, llist((dh, dt) ->
								Goals.<LList<A>> exist(rem -> dh.unify(head)
										.and(removo(any, rem, head).debug("removo",
														HashMap.of("any", any, "distinct", distinct)),
												defer(() -> distincto(rem, dt)))))))));
	}

	@Test
	public void shouldMakeDistinct() {
		Unifiable<LList<Integer>> r = lvar();
		System.out.println(runStream(r,
				Goals.<LList<Integer>> exist(l ->
						l.unify(LList.ofAll(1, 2))
								.and(Goals.distincto(r))))
				.limit(5)
				.map(Objects::toString)
				.collect(Collectors.joining("\n")));
	}

	@Test
	public void shouldApplyFirst() {
		Unifiable<Integer> l = lvar();
		System.out.println(runStream(l, firsto(
				l.unify(1).and(l.separate(1)),
				l.unify(2).or(l.unify(4)),
				l.unify(3)))
				.collect(Collectors.toList()));
	}

	@Test
	public void shouldReturnFromSingleGoalThatSucceeds() {
		Unifiable<Integer> x = lvar();
		List<Integer> results = Goal.conda(
						x.separate(x),
						x.unify(1).or(x.unify(2)),
						x.unify(3))
				.solve(x)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactly(1, 2);
	}

	@Test
	public void shouldReturnSingleElementFromSingleGoalThatSucceeds() {
		Unifiable<Integer> x = lvar();
		List<Integer> results = Goal.condu(
						x.separate(x),
						x.unify(1).or(x.unify(2)),
						x.unify(3))
				.solve(x)
				.map(Unifiable::get)
				.collect(Collectors.toList());

		Assertions.assertThat(results)
				.containsExactly(1);
	}
}