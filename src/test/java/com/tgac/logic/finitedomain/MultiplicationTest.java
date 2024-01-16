package com.tgac.logic.finitedomain;
import com.tgac.logic.Goal;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.finitedomain.domains.Singleton;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import lombok.var;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.tgac.logic.Matche.llist;
import static com.tgac.logic.Matche.matche;
import static com.tgac.logic.finitedomain.FiniteDomain.addo;
import static com.tgac.logic.finitedomain.FiniteDomain.copyDomain;
import static com.tgac.logic.finitedomain.FiniteDomain.divo;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.separate;
import static com.tgac.logic.finitedomain.FiniteDomain.subtracto;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
@SuppressWarnings("unchecked")
public class MultiplicationTest {

	@Test
	public void shouldMultiply() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(dom(a, Interval.of(-2, 2)))
						.and(dom(b, Interval.of(-2, 2)))
						.and(c.unify(-4))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(Tuple.of(-2, 2, -4), Tuple.of(2, -2, -4));
	}

	@Test
	public void shouldMultiply2() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(a.unify(-2))
						.and(dom(b, Interval.of(-2, 2)))
						.and(dom(c, Interval.of(-4, 4)))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(
						Tuple.of(-2, -2, 4),
						Tuple.of(-2, -1, 2),
						Tuple.of(-2, 0, 0),
						Tuple.of(-2, 1, -2),
						Tuple.of(-2, 2, -4));
	}

	@Test
	public void shouldMultiply3() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(dom(a, Interval.of(-2, 2)))
						.and(b.unify(-2))
						.and(dom(c, Interval.of(-4, 4)))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(
						Tuple.of(-2, -2, 4),
						Tuple.of(-1, -2, 2),
						Tuple.of(0, -2, 0),
						Tuple.of(1, -2, -2),
						Tuple.of(2, -2, -4));
	}

	@Test
	public void shouldMultiplyZero() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(a.unify(0))
						.and(dom(b, Interval.of(-2, 2)))
						.and(dom(c, Interval.of(-4, 4)))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(
						Tuple.of(0, -2, 0),
						Tuple.of(0, -1, 0),
						Tuple.of(0, 0, 0),
						Tuple.of(0, 1, 0),
						Tuple.of(0, 2, 0));
	}

	@Test
	public void shouldMultiplyZero2() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(dom(a, Interval.of(-2, 2)))
						.and(b.unify(0))
						.and(dom(c, Interval.of(-4, 4)))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(
						Tuple.of(-2, 0, 0),
						Tuple.of(-1, 0, 0),
						Tuple.of(0, 0, 0),
						Tuple.of(1, 0, 0),
						Tuple.of(2, 0, 0));
	}

	@Test
	public void shouldMultiplyZero3() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(dom(a, Interval.of(-2, 2)))
						.and(dom(b, Interval.of(-2, 2)))
						.and(c.unify(0))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(
						Tuple.of(0, -2, 0),
						Tuple.of(0, -1, 0),
						Tuple.of(0, 1, 0),
						Tuple.of(0, 2, 0),
						Tuple.of(-2, 0, 0),
						Tuple.of(-1, 0, 0),
						Tuple.of(0, 0, 0),
						Tuple.of(1, 0, 0),
						Tuple.of(2, 0, 0));
	}

	@Test
	public void shouldMultiplyInterval() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();

		List<Tuple3<Integer, Integer, Integer>> collect =
				FiniteDomain.multo(a, b, c)
						.and(dom(a, Interval.of(-2, 2)))
						.and(dom(b, Interval.of(-2, 2)))
						.and(dom(c, Interval.of(-4, 4)))
						.solve(lval(Tuple.of(a, b, c)))
						.map(Unifiable::get)
						.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get))
						.collect(Collectors.toList());

		System.out.println(collect);

		Assertions.assertThat(collect)
				.containsExactlyInAnyOrder(
						Tuple.of(-2, -2, 4),
						Tuple.of(-2, -1, 2),
						Tuple.of(-2, 0, 0),
						Tuple.of(-2, 1, -2),
						Tuple.of(-2, 2, -4),
						Tuple.of(-1, -2, 2),
						Tuple.of(-1, -1, 1),
						Tuple.of(-1, 0, 0),
						Tuple.of(-1, 1, -1),
						Tuple.of(-1, 2, -2),
						Tuple.of(0, -2, 0),
						Tuple.of(0, -1, 0),
						Tuple.of(0, 0, 0),
						Tuple.of(0, 1, 0),
						Tuple.of(0, 2, 0),
						Tuple.of(1, -2, -2),
						Tuple.of(1, -1, -1),
						Tuple.of(1, 0, 0),
						Tuple.of(1, 1, 1),
						Tuple.of(1, 2, 2),
						Tuple.of(2, -2, -4),
						Tuple.of(2, -1, -2),
						Tuple.of(2, 0, 0),
						Tuple.of(2, 1, 2),
						Tuple.of(2, 2, 4));
	}

	public static <T> Goal divoWithRest(Unifiable<T> divided, Unifiable<T> divisor, Unifiable<T> rest, Unifiable<T> result) {
		Unifiable<T> tmp = lvar();
		return copyDomain(divided, tmp)
				.and(divo(divided, divisor, tmp))
				.and(addo(tmp, rest, result))
				.named(divided + " / " + divisor + " = " + result + ", % " + rest);
	}

	@Test
	public void shouldDivideWithRest() {
		Unifiable<Integer> divided = lvar();
		Unifiable<Integer> divisor = lvar();
		Unifiable<Integer> rest = lvar();
		Unifiable<Integer> result = lvar();
		var results = Goal.success()
				.and(dom(divided, Interval.of(-15, 15)))
				.and(dom(divisor, Singleton.of(3)))
				.and(dom(result, Singleton.of(-3)))
				.and(dom(rest, Interval.of(-2, 2)))
				.and(divoWithRest(divided, divisor, rest, result))
				.solve(lval(Tuple.of(divided, divisor, rest, result)))
				.map(Unifiable::get)
				.map(t -> t.map(Unifiable::get, Unifiable::get, Unifiable::get, Unifiable::get))
				.collect(Collectors.toList());

		System.out.println(results);
		Assertions.assertThat(results)
				.containsExactlyInAnyOrder(
						Tuple.of(-6, 3, -1, -3),
						Tuple.of(-3, 3, -2, -3),
						Tuple.of(-9, 3, 0, -3),
						Tuple.of(-12, 3, 1, -3),
						Tuple.of(-15, 3, 2, -3)
				);
	}

	public static <U> Goal lengtho(Unifiable<Integer> count, Unifiable<LList<String>> lst) {
		Unifiable<Integer> tmp = lvar("tmp");
		return matche(lst,
				llist(() -> count.unify(0)),
				llist((a, d) ->
						//						Goal.success()
						separate(count, lval(0))
								.and(subtracto(count, lval(1), tmp))
								.and(Goal.defer(() -> lengtho(tmp, d)))));
	}

	@Test
	@Ignore
	public void tst() {
		Unifiable<LList<String>> lst = lvar("lst");
		Unifiable<Integer> count = lvar("count");
		System.out.println(
				dom(count, Interval.of(0, 3))
						.and(lengtho(count, lst))
						.solve(lst)
						//						.limit(4)
						.map(Unifiable::get)
						.collect(Collectors.toList()));
	}

	//	public static <T> Goal mulo(Unifiable<T> lhs, Unifiable<T> rhs, Unifiable<T> result){
	//		Unifiable<T> tmp = lvar();
	//		Unifiable<T> tmp1 = lvar();
	//		return copyDomain(lhs, tmp)
	//				.and(copyDomain())
	//				.and()
	//	}
}
