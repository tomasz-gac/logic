package com.tgac.logic;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.recursion.SimpleEngine;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.LVal;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.Stream;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.val;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class LogicTest {

	public <T> Goal caro(
			Unifiable<T> lhs,
			Unifiable<LList<T>> cons) {
		return cons.unify(LList.of(lhs, lvar()));
	}

	@Test
	public void shouldConde() {
		Unifiable<Integer> x = lvar();
		System.out.println(
				Utils.collect(x.unify(1).or(x.unify(2)).or(x.unify(3))
						.solve(x)));
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
		System.out.println(Logic.appendo(lst, x, res));

		val out = lval(Tuple.of(lst, x, res));
		val results = runStream(out,
				Logic.appendo(lst, x, res))
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
				Logic.appendo(lst, x, res))
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
				Logic.appendo(lst, x, res))
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
				Logic.appendo(lst, x, res))
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
				Logic.sameLengtho(lhs, rhs))
				.limit(10)
				.collect(Collectors.toList());
		System.out.println(results);
		results.forEach(t ->
				assertThat(t.get()._1.get().stream().count())
						.isEqualTo(t.get()._2.get().stream().count()));
	}

	public <A> Goal reversoAcc(
			Unifiable<LList<A>> list,
			Unifiable<LList<A>> accumulator,
			Unifiable<LList<A>> result) {

		Unifiable<A> head = lvar("H"); // Using named lvars for clarity
		Unifiable<LList<A>> tail = lvar("T");
		Unifiable<LList<A>> newAccumulator = lvar("NewAcc");

		// Base case: list is empty, the accumulator is the result
		Goal baseCase = list.unify(LList.empty())
				.and(accumulator.unify(result));

		// Recursive case:
		// list = [head | tail]
		// newAccumulator = [head | accumulator]
		// recurse with reversoAcc(tail, newAccumulator, result)
		Goal recursiveCase = list.unify(LList.of(head, tail))
				.and(newAccumulator.unify(LList.of(head, accumulator))) // Prepend head to accumulator
				.and(Goal.defer(() -> reversoAcc(tail, newAccumulator, result)));

		return baseCase.or(recursiveCase);
	}

	public <A> Goal efficientReverso(Unifiable<LList<A>> list, Unifiable<LList<A>> reversedList) {
		// Initial call with an empty accumulator
		return reversoAcc(list, LList.empty(), reversedList);
	}

	public <A> Goal reversoUnsafe(Unifiable<LList<A>> lhs, Unifiable<LList<A>> rhs) {
		Unifiable<A> rHead = lvar();
		Unifiable<LList<A>> rTail = lvar();
		Unifiable<LList<A>> res = lvar();
		return rhs.unify(LList.empty()).and(lhs.unify(LList.empty()))
				.or(rhs.unify(LList.of(rHead, rTail))
						.and(Logic.appendo(res, LList.of(rHead), lhs))
						.and(Goal.defer(() -> reversoUnsafe(rTail, res))));
	}

	public <A> Goal reverso(Unifiable<LList<A>> lhs, Unifiable<LList<A>> rhs) {
		return Logic.sameLengtho(lhs, rhs).and(reversoUnsafe(lhs, rhs));
	}

	@Test
	public void shouldReverse() {
		Unifiable<LList<Integer>> reversed = lvar();
		System.out.println(reverso(reversed,
				LList.ofAll(
						IntStream.range(0, 5)
								.boxed()
								.collect(Collectors.toList()))));
		val run = runStream(reversed,
				reverso(reversed,
						LList.ofAll(
								IntStream.range(0, 5)
										.boxed()
										.collect(Collectors.toList()))))
				.collect(Collectors.toList());

		System.out.println(run);
		assertThat(run.get(0))
				.isEqualTo(lval(Stream.range(0, 5)
						.map(i -> 5 - i - 1)
						.map(LVal::lval)
						.collect(LList.collector()))
						.get());
	}

	@Test
	public void shouldReverse2() {
		Unifiable<LList<Integer>> normal = lvar();
		Unifiable<LList<Integer>> reversed = lvar();
		System.out.println(reverso(
				normal,
				reversed));
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

		// TODO - assert
		System.out.println(runStream(normal,
				Logic.sameLengtho(normal, LList.ofAll(IntStream.range(0, 100)
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
						.and(Logic.appendo(middle, LList.of(head), tail))
						.and(Goal.defer(() -> palindromo(middle))));
	}

	public <A> Goal palindromo2(Unifiable<LList<A>> palindrome) {
		return reverso(palindrome, palindrome);
	}

	public <A> Goal palindromo3(Unifiable<LList<A>> palindrome) {
		return efficientReverso(palindrome, palindrome);
	}

	@Test
	public void shouldWritePalindrome() {
		Unifiable<LList<Integer>> lst = lvar();
		int n = 100;
		try (
				val solved =
						Logic.sameLengtho(lst, LList.ofAll(Stream.range(0, n).collect(Collectors.toList())))
								.and(palindromo2(lst))
								.solve(lst)
		) {
			val collected = solved
					.findFirst()
					.get()
					.get()
					.stream()
					.map(Either::get)
					.collect(Collectors.toList());
			System.out.println(collected);
			for (int i = 0; i < n / 2; ++i) {
				assertThat(collected.get(i))
						.isEqualTo(collected.get(n - 1 - i));
			}
		}
	}

	@Test
	public void shouldFindMember() {
		Unifiable<Integer> x = lvar();
		Unifiable<LList<Integer>> lst = lvar();

		List<Integer> xs = runStream(x,
				lst.unify(LList.ofAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
				Logic.membero(x, lst))
				.map(Unifiable::get)
				.collect(Collectors.toList());
		System.out.println(xs);
		assertThat(xs)
				.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
	}

	static <A> Goal lists(Unifiable<LList<A>> lists) {
		return lists.unify(LList.empty())
				.or(Logic.<A, LList<A>> exist((head, tail) ->
						lists.unify(LList.of(head, tail))
								.and(Goal.defer(() -> lists(tail)))));
	}

	@Test
	public void shouldRefreshVariablesOnDisjunction() {
		Unifiable<LList<Integer>> lst = lvar();
		System.out.println(runStream(lst, lists(lst))
				.limit(4)
				.collect(Collectors.toList()));
	}

	@Test
	public void shouldComputeAnd() {
		Unifiable<Tuple3<Unifiable<Boolean>, Unifiable<Boolean>, Unifiable<Boolean>>> out = lvar();
		var result = Utils.collect(Matche.matche(out,
						Matche.tuple(Logic::conjo))
				.solve(out)
				.map(Unifiable::get)
				.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get)));

		Assertions.assertThat(result)
				.allMatch(t -> (t._1 && t._2) == t._3);
	}

	@Test
	public void shouldComputeOr() {
		Unifiable<Tuple3<Unifiable<Boolean>, Unifiable<Boolean>, Unifiable<Boolean>>> out = lvar();
		var result = Utils.collect(Matche.matche(out,
						Matche.tuple(Logic::disjo))
				.solve(out)
				.map(Unifiable::get)
				.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get)));

		Assertions.assertThat(result)
				.allMatch(t -> (t._1 || t._2) == t._3);
	}

	@Test
	public void shouldComputeAnyo() {
		Unifiable<LList<Boolean>> out = lvar();

		var result = Utils.collect(Logic.sameLengtho(LList.ofAll(Stream.range(0, 3).collect(Collectors.toList())), out)
				.and(Logic.anyo(out, lval(true)))
				.solve(out)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));

		System.out.println(result);

		Assertions.assertThat(result)
				.hasSize(7)
				.allMatch(l -> l.stream().anyMatch(x -> x));
	}

	@Test
	public void shouldComputeAnyoForFailingLists() {
		Unifiable<LList<Boolean>> out = lvar();
		var result = Utils.collect(Logic.sameLengtho(LList.ofAll(Stream.range(0, 3).collect(Collectors.toList())), out)
				.and(Logic.anyo(out, lval(false)))
				.solve(out)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactly(Arrays.asList(false, false, false));
	}

	@Test
	public void shouldComputeAlloForFail() {
		Unifiable<LList<Boolean>> out = lvar();
		var result = Utils.collect(Logic.sameLengtho(LList.ofAll(Stream.range(0, 3).collect(Collectors.toList())), out)
				.and(Logic.allo(out, lval(false)))
				.solve(out)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));

		System.out.println(result);

		Assertions.assertThat(result)
				.hasSize(7)
				.allMatch(l -> l.stream().anyMatch(x -> !x));
	}

	@Test
	public void shouldComputeAlloForSuccessList() {
		Unifiable<LList<Boolean>> out = lvar();
		var result = Utils.collect(Logic.sameLengtho(LList.ofAll(Stream.range(0, 3).collect(Collectors.toList())), out)
				.and(Logic.allo(out, lval(true)))
				.solve(out)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));

		System.out.println(result);

		Assertions.assertThat(result)
				.containsExactly(Arrays.asList(true, true, true));
	}
}