package com.tgac.logic;

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.goals.Logic;
import com.tgac.logic.goals.Matche;
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
		return cons.unifies(LList.of(lhs, lvar()));
	}

	@Test
	public void shouldConde() {
		Unifiable<Integer> x = lvar();
		System.out.println(
				Utils.collect(x.unifies(1).or(x.unifies(2)).or(x.unifies(3))
						.solve(x)));
	}

	@Test
	public void test() {
		Unifiable<Integer> head = lvar();
		Unifiable<LList<Integer>> tail = lvar();
		Unifiable<LList<Integer>> lst = lvar();
		List<Integer> collect = runStream(lst,
				lst.unifies(LList.ofAll(1, 2, 3, 4)),
				tail.unifies((LList.ofAll(2, 3, 4))),
				lst.unifies(LList.of(head, tail)))
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
				head.unifies(3),
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
				lst.unifies(LList.ofAll(1, 2, 3)),
				res.unifies(LList.ofAll(1, 2, 3, 4, 5, 6)),
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
				x.unifies(LList.ofAll(4, 5, 6)),
				res.unifies(LList.ofAll(1, 2, 3, 4, 5, 6)),
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
				x.unifies(LList.ofAll(4, 5, 6)),
				lst.unifies(LList.ofAll(1, 2, 3)),
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
		Goal baseCase = list.unifies(LList.empty())
				.and(accumulator.unifies(result));

		// Recursive case:
		// list = [head | tail]
		// newAccumulator = [head | accumulator]
		// recurse with reversoAcc(tail, newAccumulator, result)
		Goal recursiveCase = list.unifies(LList.of(head, tail))
				.and(newAccumulator.unifies(LList.of(head, accumulator))) // Prepend head to accumulator
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
		return rhs.unifies(LList.empty()).and(lhs.unifies(LList.empty()))
				.or(rhs.unifies(LList.of(rHead, rTail))
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

		Assertions.assertThat(runStream(normal,
						Logic.sameLengtho(normal, LList.ofAll(IntStream.range(0, 100)
								.boxed()
								.collect(Collectors.toList()))),
						reverso(normal, normal))
						.map(Object::toString)
						.collect(Collectors.joining("\n")))
				.isEqualTo(
						"{(<_.99>, <_.98>, <_.97>, <_.96>, <_.95>, <_.94>, <_.93>, <_.92>, "
								+ "<_.91>, <_.90>, <_.89>, <_.88>, <_.87>, <_.86>, <_.85>, <_.84>, "
								+ "<_.83>, <_.82>, <_.81>, <_.80>, <_.79>, <_.78>, <_.77>, <_.76>, "
								+ "<_.75>, <_.74>, <_.73>, <_.72>, <_.71>, <_.70>, <_.69>, <_.68>, "
								+ "<_.67>, <_.66>, <_.65>, <_.64>, <_.63>, <_.62>, <_.61>, <_.60>, "
								+ "<_.59>, <_.58>, <_.57>, <_.56>, <_.55>, <_.54>, <_.53>, <_.52>, "
								+ "<_.51>, <_.50>, <_.50>, <_.51>, <_.52>, <_.53>, <_.54>, <_.55>, "
								+ "<_.56>, <_.57>, <_.58>, <_.59>, <_.60>, <_.61>, <_.62>, <_.63>, "
								+ "<_.64>, <_.65>, <_.66>, <_.67>, <_.68>, <_.69>, <_.70>, <_.71>, "
								+ "<_.72>, <_.73>, <_.74>, <_.75>, <_.76>, <_.77>, <_.78>, <_.79>, "
								+ "<_.80>, <_.81>, <_.82>, <_.83>, <_.84>, <_.85>, <_.86>, <_.87>, "
								+ "<_.88>, <_.89>, <_.90>, <_.91>, <_.92>, <_.93>, <_.94>, <_.95>, "
								+ "<_.96>, <_.97>, <_.98>, <_.99>)}");
	}

	public <A> Goal palindromo(Unifiable<LList<A>> palindrome) {
		Unifiable<A> head = lvar();
		Unifiable<LList<A>> tail = lvar();
		Unifiable<LList<A>> middle = lvar();
		return palindrome.unifies(LList.empty())
				.or(palindrome.unifies(LList.of(head, tail))
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
				lst.unifies(LList.ofAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
				Logic.membero(x, lst))
				.map(Unifiable::get)
				.collect(Collectors.toList());
		System.out.println(xs);
		assertThat(xs)
				.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
	}

	static <A> Goal lists(Unifiable<LList<A>> lists) {
		return lists.unifies(LList.empty())
				.or(Logic.<A, LList<A>> exist((head, tail) ->
						lists.unifies(LList.of(head, tail))
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

	@Test
	public void foldRightTest() {
		Unifiable<Integer> result = lvar();

		Assertions.assertThat(LList.foldRight(
								LList.ofAll(1, 2, 3, 4, 5, 6),
								lval(5),
								result,
								(acc, lhs, rhs) -> Logic.project(lhs, rhs, (l, r) -> acc.unifies(l + r)))
						.solve(result)
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactly(26);
	}

	@Test
	public void foldRightTest2() {
		Unifiable<Integer> result = lvar();

		Assertions.assertThat(LList.foldRight(
								LList.ofAll(30, 15, 10, 5),
								lval(0),
								result,
								(acc, lhs, rhs) -> Logic.project(lhs, rhs, (l, r) -> acc.unifies(l - r)))
						.solve(result)
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactly(-60);
	}

	@Test
	public void foldLeftTest() {
		Unifiable<Integer> result = lvar();

		Assertions.assertThat(LList.foldLeft(
								LList.ofAll(30, 15, 10, 5),
								lval(60),
								result,
								(acc, lhs, rhs) -> Logic.project(lhs, rhs, (l, r) -> acc.unifies(l - r)))
						.solve(result)
						.map(Unifiable::get)
						.collect(Collectors.toList()))
				.containsExactly(0);
	}
}