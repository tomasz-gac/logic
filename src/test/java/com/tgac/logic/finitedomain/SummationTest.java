package com.tgac.logic.finitedomain;

import static com.tgac.logic.finitedomain.FiniteDomain.addo;
import static com.tgac.logic.finitedomain.FiniteDomain.dom;
import static com.tgac.logic.finitedomain.FiniteDomain.leq;
import static com.tgac.logic.finitedomain.FiniteDomain.lss;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.goals.Goal;
import com.tgac.logic.Utils;
import com.tgac.logic.finitedomain.domains.Interval;
import com.tgac.logic.unification.LList;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class SummationTest {

	@Test
	public void shouldSum() {
		Unifiable<Long> i = lvar();
		Unifiable<Long> j = lvar();
		Unifiable<Long> k = lvar();

		Instant start = Instant.now();

		Goal goal =
				addo(i, j, k)
						.and(FiniteDomain.separate(i, j))
						.and(dom(i, Interval.of(0L, 100)))
						.and(dom(j, Interval.of(0L, 100L)))
						.and(dom(k, Interval.of(0L, 100L)));

		System.out.println(goal);

		java.util.List<Tuple3<Long, Long, Long>> result =
				Utils.collect(goal.solve(lval(Tuple.of(i, j, k)))
						.map(Unifiable::get)
						.map(t -> t
								.map1(Unifiable::get)
								.map2(Unifiable::get)
								.map3(Unifiable::get)));

		System.out.println(Duration.between(start, Instant.now()).toMillis() / 1000.);
		System.out.println(result.size());

		Assertions.assertThat(result.stream().allMatch(t -> t._1 + t._2 == t._3))
				.isTrue();
		Assertions.assertThat(result.stream().noneMatch(t -> t._1.equals(t._2)))
				.isTrue();
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
				.and(addo(augend, addend, partialSum))
				.and(addo(partialSum, carryIn, sum))
				.and(Goal.failure()
						.or(lss(lval(9), sum)
								.and(carryOut.unify(1))
								.and(addo(digit, lval(10), sum)))
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
		Unifiable<LList<Integer>> lst = LList.ofAll(s, e, n, d, m, o, r, y);
		return letters.unify(lst)
				.and(FiniteDomainTest.distinctoFd(Arrays.asList(s, e, n, d, m, o, r, y)))
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

		List<List<Integer>> result = Utils.collect(sendMoreMoneyo(letters)
				.solve(letters)
				.map(Unifiable::get)
				.map(l -> l.toValueStream().collect(Collectors.toList())));

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

	@Test
	public void testAsNumber() {
		Assertions.assertThat(
						asNumber(Arrays.asList(1, 2, 3, 4)))
				.isEqualTo(1234);
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

	@Test(expected = RuntimeException.class)
	public void shouldNotSumWhenMissingDomain() {
		Unifiable<Integer> a = lvar();
		Unifiable<Integer> b = lvar();
		Unifiable<Integer> c = lvar();
		System.out.println(Utils.collect(addo(a, b, c)
				.and(dom(a, Interval.of(0, 100)))
				.and(dom(c, Interval.of(0, 100)))
				.solve(lval(Tuple.of(a, b, c)))
				.map(Unifiable::get)));
	}
}
